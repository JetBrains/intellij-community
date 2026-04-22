// COMPILER_ARGUMENTS: -Xcollection-literals
// PROBLEM: none
val array: Array<String> = arrayOf<caret>("a", "b", "c").copyOf()