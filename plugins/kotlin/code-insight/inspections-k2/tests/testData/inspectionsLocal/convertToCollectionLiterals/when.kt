// COMPILER_ARGUMENTS: -Xcollection-literals

val x = when {
    true -> lis<caret>tOf()
    else -> emptyList<String>()
}