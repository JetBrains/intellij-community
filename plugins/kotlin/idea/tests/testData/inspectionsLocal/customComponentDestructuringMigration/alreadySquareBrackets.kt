// PROBLEM: none
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
// WITH_STDLIB

fun test() {
    val [<caret>x, y] = listOf(1, 2)
}