// COMPILER_ARGUMENTS: -Xcollection-literals
fun test(): List<String>  {
    return listOf<caret>("a", "b", "c")
}