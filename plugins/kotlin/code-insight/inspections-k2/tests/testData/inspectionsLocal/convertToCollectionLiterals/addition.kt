// COMPILER_ARGUMENTS: -Xcollection-literals
// PROBLEM: none

fun test() {
    val a: MutableList<Int> = []
    a += lis<caret>tOf(1, 2, 3)
}
