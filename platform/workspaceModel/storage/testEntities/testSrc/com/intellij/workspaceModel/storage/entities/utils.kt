import com.intellij.workspaceModel.storage.EntitySource
import java.io.File
//import kotlin.test.assertEquals

internal object MySource : EntitySource
internal object AnotherSource : EntitySource

//internal fun generateEntities() {
//    val deftRoot = File("").absoluteFile
//
//    val dir = deftRoot.resolve("community/platform/workspaceModel/codegen/test/testSrc/com/intellij/workspaceModel/model")
//    CodeWriter().generate(dir, "ext", "ext/impl", "org.jetbrains.deft.IntellijWsTestIjExt")
//}
//
//internal fun assertEntities() {
//    val deftRoot = File("").absoluteFile
//
//    val dir = deftRoot.resolve("plugins/workspaceModel/model")
//    CodeAsserter().generate(dir, "testing", "testing/impl", "org.jetbrains.deft.IntellijWsTest")
//}

//class CodeAsserter : CodeWriter() {
//    override fun File.writeCode(code: String) {
//        assertEquals(this.readText(), code, this.path)
//    }
//}
