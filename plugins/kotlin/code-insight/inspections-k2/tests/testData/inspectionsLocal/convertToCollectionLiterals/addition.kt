// COMPILER_ARGUMENTS: -Xcollection-literals
// PROBLEM: none

val a: MutableList<Int> = []
a += lis<caret>tOf(1, 2, 3)