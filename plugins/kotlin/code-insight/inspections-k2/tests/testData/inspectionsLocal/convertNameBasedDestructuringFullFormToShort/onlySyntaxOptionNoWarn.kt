// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
// PROBLEM: none

data class Foo(val x: String, val y: Int)

fun test() {
    (<caret>val x, val y) = Foo("", 0)
}
