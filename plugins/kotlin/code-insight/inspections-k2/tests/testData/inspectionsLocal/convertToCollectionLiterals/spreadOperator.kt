// COMPILER_ARGUMENTS: -Xcollection-literals
// PROBLEM: none
fun test(arr: Array<String>) {
    val list = <caret>listOf(*arr)
}
