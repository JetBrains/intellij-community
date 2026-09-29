// PROBLEM: none
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax

class Foo {
    val bar: String get() { throw IllegalStateException("side effect") }
    val qux: Int get() = 0
}

fun test() {
    (val <caret>_ = bar, val qux) = Foo()
}