// COMPILER_ARGUMENTS: -Xcollection-literals
// PROBLEM: none
val list = ["first"] + list<caret>Of("second")