// PROBLEM: none
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax

fun test() {
    val [<caret>_, x] = 1 to 2
}
