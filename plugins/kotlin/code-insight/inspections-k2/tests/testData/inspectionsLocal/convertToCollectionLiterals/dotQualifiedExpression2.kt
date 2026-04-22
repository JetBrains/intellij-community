// COMPILER_ARGUMENTS: -Xcollection-literals
// PROBLEM: none
val contains = setOf<caret>("1", "2", "3").contains("1")