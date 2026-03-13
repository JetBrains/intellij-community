// PROBLEM: none
// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete
// WITH_STDLIB
// K2_ERROR: Unresolved reference 'x'.
// K2_ERROR: Unresolved reference 'y'.

fun test() {
    val (<caret>x, y) = listOf(1, 2)
}