// COMPILER_ARGUMENTS: -Xcollection-literals
// PROBLEM: none
val x = when {
    true -> listOf()
    else -> emp<caret>tyList<String>()
}