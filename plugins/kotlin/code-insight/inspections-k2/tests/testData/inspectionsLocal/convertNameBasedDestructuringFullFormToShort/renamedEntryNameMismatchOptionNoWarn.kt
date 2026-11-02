// COMPILER_ARGUMENTS: -Xname-based-destructuring=name-mismatch
// PROBLEM: none

data class Foo(val x: String, val y: Int)

fun test() {
    (<caret>val renamed = x, val y) = Foo("", 0)
}
