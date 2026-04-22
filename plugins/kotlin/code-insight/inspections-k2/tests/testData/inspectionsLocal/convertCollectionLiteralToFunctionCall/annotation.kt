// COMPILER_ARGUMENTS: -Xcollection-literals
// PROBLEM: none
annotation class Ann(val values: IntArray)

@Ann(values = [<caret>1, 2, 3])
fun foo() {}
