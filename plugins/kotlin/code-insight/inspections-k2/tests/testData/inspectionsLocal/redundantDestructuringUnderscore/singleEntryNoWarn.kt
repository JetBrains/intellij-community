// PROBLEM: none
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax

data class Foo(val bar: String)

fun test() {
    (val <caret>_ = bar) = Foo("")
}
