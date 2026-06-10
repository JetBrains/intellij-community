// COMPILER_ARGUMENTS: -Xcollection-literals
// PROBLEM: none
val x = when {
    true -> listOf<String>()
    else -> emp<caret>tyList()
}