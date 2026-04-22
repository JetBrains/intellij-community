// COMPILER_ARGUMENTS: -Xcollection-literals
// PROBLEM: none
interface I

val x = emptyList<I><caret>().forEach {
}