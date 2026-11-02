// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete
// PROBLEM: none

data class Foo(val x: String, val y: Int)

fun test() {
    (<caret>val x, var y) = Foo("", 0)
}
