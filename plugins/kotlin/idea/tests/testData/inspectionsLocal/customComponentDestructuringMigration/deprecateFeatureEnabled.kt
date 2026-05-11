// PROBLEM: none
// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete
// WITH_STDLIB
// K2_ERROR: Unresolved reference 'x' on receiver of type 'List<Int>'.
// K2_ERROR: Unresolved reference 'y' on receiver of type 'List<Int>'.

fun test() {
    val (<caret>x, y) = listOf(1, 2)
}