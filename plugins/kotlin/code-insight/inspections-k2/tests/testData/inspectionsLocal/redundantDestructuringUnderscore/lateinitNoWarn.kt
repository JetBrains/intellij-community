// PROBLEM: none
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax

class Foo {
    lateinit var bar: String
    val qux: Int = 0
}
fun main() {
    (val <caret>_ = bar, val qux) = Foo()
}