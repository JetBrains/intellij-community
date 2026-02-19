// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
import org.intellij.lang.annotations.Language

object Foo {
    operator fun get(@Language("kotlin") key: String): String = key
    operator fun get(@Language("kotlin") key1: String, @Language("kotlin") key2: String): String = key1 + key2
    operator fun set(@Language("kotlin") key: String, @Language("kotlin") value: String) {}
    operator fun set(@Language("kotlin") key1: String, @Language("kotlin") key2: String, @Language("kotlin") value: String) {}
    operator fun plus(@Language("kotlin") value: String) {}
    operator fun minus(@Language("kotlin") value: String) {}
    operator fun div(@Language("kotlin") value: String) {}
}

fun main() {
    val x = Foo["val a = 1"]
    val y = Foo[("val a = 42"), "val b = 64"]
    Foo["val a = 1"] = "fun main(){}"
    Foo["val a = 1", ("class F")] = "fun main(){}"
    Foo + "class F"
    Foo + ("class F")
    Foo - "object X {}"
    Foo / "val foo = bar()"
}