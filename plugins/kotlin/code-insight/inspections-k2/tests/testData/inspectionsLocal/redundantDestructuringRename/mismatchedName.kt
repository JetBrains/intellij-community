// PROBLEM: none
// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete

data class Foo(val bar: String)

fun test() {
    (val baz = <caret>bar) = Foo("")
}
