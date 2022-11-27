import com.intellij.database.extensions.Clipboard
import com.intellij.database.extensions.Files
import com.intellij.database.model.DasColumn
import com.intellij.database.model.DasObject
import com.intellij.database.model.DasTable
import com.intellij.database.model.ObjectKind
import com.intellij.database.util.Case
import com.intellij.database.util.DasUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.util.containers.JBIterable

/**
 * 环境
 */
final Files files = (Files) FILES; FILES = null;
final Project project = (Project) PROJECT; PROJECT = null;
final JBIterable<DasObject> dasObjects = (JBIterable<DasObject>) SELECTION; SELECTION = null;
final Clipboard clipboard = (Clipboard) CLIPBOARD; CLIPBOARD = null;

// 遍历表，调用模板生成
files.chooseDirectoryAndSave("选择保存目录", "选择保存目录") {root ->
    dasObjects.filter(DasTable.class).each {table ->
        Config.generators.each { it.generate(table, root) }
    }
}


/**
 * 配置
 */
class Config {
    static def packageName = "com.sample"
    static def author = "yemh"
    static def generators = [new MybatisGenerator()]
    static def typeMapping = [
    (~/(?i)int/)                      : "long",
    (~/(?i)float|double|decimal|real/): "double",
    (~/(?i)datetime|timestamp/)       : "java.sql.Timestamp",
    (~/(?i)date/)                     : "java.sql.Date",
    (~/(?i)time/)                     : "java.sql.Time",
    (~/(?i)/)                         : "String"
    ]
}

interface Generator {
    /**
     * 模板生成入口
     * @param table 表元数据
     * @param root 生成目标目录
     */
    def generate(DasTable table, File root);
    def template(PrintWriter out);
    /**
     * 文件保存
     * @param path
     * @param fileName
     * @return
     */
    default def runAndSave(File path, String fileName) {
        path.mkdirs()
        new File(path, fileName).withPrintWriter(out->template(out))
    }

    default File packagePath(File root, String packageName) {
        new File(root.getPath() + "/" + packageName.replace(".", "/"))
    }

}

class DTOGenerator implements Generator {

    String entityName
    DasTable table
    List fields
    String packageName

    @Override
    def generate(DasTable table, File root) {
        setTable(table)
        setEntityName(Tools.javaName(table.getName(), true) + "DTO")
        setFields(Tools.calcFields(table))
        setPackageName(Config.packageName + ".dto")
        runAndSave(packagePath(root, packageName), entityName + ".java")
    }

    @Override
    def template(PrintWriter out) {
        out.println "package $packageName;"
        out.println ""
        out.println "import lombok.Data;"
        out.println "import java.io.Serializable;"
        out.println ""
        out.println "/**"
        out.println " * ${table.getComment()}(${table.getName()})实体类"
        out.println " * "
        out.println " * @author ${Config.author}"
        out.println " */"
        out.println "@Data"
        out.println "public class ${entityName} implements Serializable {"
        out.println "  private static final long serialVersionUID = 1L;"
        fields.each() {
            if (it.annos != "") {
                out.println "  /**"
                out.println "   * ${it.annos}"
                out.println "   */"
            }
            out.println "  private ${it.type} ${it.name};"
        }
        out.println "}"
    }
}

class DAOGenerator implements Generator {

    String entityName
    String daoName
    DasTable table
    List fields
    String packageName

    @Override
    def generate(DasTable table, File root) {
        setEntityName(Tools.javaName(table.getName(), true) + "DTO")
        setDaoName(Tools.javaName(table.getName(), true) + "DAO")
        setFields(Tools.calcFields(table))
        setTable(table)
        setPackageName(Config.packageName + ".dao")
        runAndSave(packagePath(root, packageName), daoName + ".java")
    }

    @Override
    def template(PrintWriter out) {
        out.println "package $packageName;"
        out.println ""
        out.println "import org.apache.ibatis.annotations.Param;"
        out.println "import java.util.List;"
        out.println ""
        out.println "/**"
        out.println " * ${table.getComment()}(${table.getName()})数据库访问层"
        out.println " * "
        out.println " * @author ${Config.author}"
        out.println " */"
        out.println "public interface ${daoName} {"
        out.println "  /**"
        out.println "   * 通过主建查询单条数据"
        out.println "   * "
        fields.findAll {it.pk}.each {
            out.println "   * @param $it.name $it.annos"
        }
        out.println "   * @return 实例对象"
        out.println "   */"
        out.println "  ${entityName} queryByKey(${fields.findAll(c->c.pk).collect(c->"@Param(\"$c.name\")" + c.type + " " + c.name).join(", ")});"
        out.println ""
        out.println "  /**"
        out.println "   * 通过实体作为筛选条件查询"
        out.println "   * "
        out.println "   * @param ${entityName.uncapitalize()} 实例对象"
        out.println "   * @return 对象列表"
        out.println "   */"
        out.println "  List<${entityName}> queryList(${entityName} ${entityName.uncapitalize()});"
        out.println ""
        out.println "  /**"
        out.println "   * 新增"
        out.println "   * "
        out.println "   * @param ${entityName.uncapitalize()} 实例对象"
        out.println "   * @return 影响行数"
        out.println "   */"
        out.println "  int insert(${entityName} ${entityName.uncapitalize()});"
        out.println ""
        out.println "  /**"
        out.println "   * 修改"
        out.println "   * "
        out.println "   * @param ${entityName.uncapitalize()} 实例对象"
        out.println "   * @return 影响行数"
        out.println "   */"
        out.println "  int update(${entityName} ${entityName.uncapitalize()});"
        out.println ""
        out.println "  /**"
        out.println "   * 删除"
        out.println "   * "
        out.println "   * @param ${entityName.uncapitalize()} 实例对象"
        out.println "   * @return 影响行数"
        out.println "   */"
        out.println "  int delete(${entityName} ${entityName.uncapitalize()});"
        out.println ""
        out.println "}"
    }
}

class MybatisGenerator implements Generator {

    String entityName
    String daoName
    List fields
    DasTable table
    String packageName

    @Override
    def generate(DasTable table, File root) {
        setEntityName(Tools.javaName(table.getName(), true) + "DTO")
        setDaoName(Tools.javaName(table.getName(), true) + "DAO")
        setFields(Tools.calcFields(table))
        setTable(table)
        setPackageName(Config.packageName + ".dao")
        runAndSave(packagePath(root, "mapper"), Tools.javaName(table.getName(), true) + "Mapper.xml")
    }

    @Override
    def template(PrintWriter out) {
        out.println(table.getName())
    }
}


/**
 * 通用方法
 */
class Tools {
    /**
     * 标识符转换
     * @param str
     * @param capitalize
     * @return
     */
    static String javaName(String str, Boolean capitalize) {
        def s = NameUtil.splitNameIntoWords(str)
                .collect { Case.LOWER.apply(it).capitalize() }
                .join("")
                .replaceAll(/[^\p{javaJavaIdentifierPart}[_]]/, "_")
        capitalize || s.length() == 1 ? s : Case.LOWER.apply(s[0]) + s[1..-1]
    }

    /**
     * 表字段处理
     * @param table
     * @return
     */
    static List calcFields(DasTable table) {
        def pks = DasUtil.getPrimaryKey(table).columnsRef.names().collect()
        DasUtil.getColumns(table).reduce([]) { fields, DasColumn col ->
            def spec = Case.LOWER.apply(col.getDataType().getSpecification())
            def typeStr = Config.typeMapping.find { p, t -> p.matcher(spec).find() }.value
            fields += [[name      : javaName(col.getName(), false),
                        columnName: col.getName(),
                        type      : typeStr,
                        pk        : pks.contains(col.getName()),
                        annos     : col.getComment()]]
        }
    }

}
