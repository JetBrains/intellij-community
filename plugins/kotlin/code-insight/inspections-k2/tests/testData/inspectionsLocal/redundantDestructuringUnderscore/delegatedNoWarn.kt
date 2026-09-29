// PROBLEM: none
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax

class Foo {
    val bar: String by lazy { "" }
    val qux: Int = 0
}

fun test() {
    (val <caret>_ = bar, val qux) = Foo()
}
