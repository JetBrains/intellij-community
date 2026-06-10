// COMPILER_ARGUMENTS: -Xcollection-literals
// PROBLEM: none

val mutableList: MutableList<Int> = []
val list: List<Int> = []
val x123 = when {
     true -> mutableList
     false -> list
     else -> list<caret>Of()
}