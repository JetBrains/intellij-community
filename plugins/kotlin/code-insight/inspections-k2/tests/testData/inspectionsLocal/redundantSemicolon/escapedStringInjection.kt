// HOST_EDITOR
import org.intellij.lang.annotations.Language

fun test() {
    @Language("kotlin")
    val code = "val text = \"value\"<caret>;"
}