class Foo<G>

fun <caret>Foo<Int>.fn(p: String) {}

fun usages() {
    val v = Foo<Int>::fn
}