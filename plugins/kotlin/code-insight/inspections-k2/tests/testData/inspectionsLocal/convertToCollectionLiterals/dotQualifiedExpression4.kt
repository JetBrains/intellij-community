// COMPILER_ARGUMENTS: -Xcollection-literals
// PROBLEM: none

class A {}
val x = A().to(setOf<caret>("1", "2", "3"))