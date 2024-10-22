// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
import org.intellij.lang.annotations.Language

object Foo {
    operator fun set(@Language("kotlin") key: String, @Language("kotlin") value: String) {}
    operator fun plus(@Language("kotlin") value: String) {}
}

fun main() {
    Foo["val a = 1"] = "fun main(){}"
    Foo + "class F"
}