// COMPILER_ARGUMENTS: -Xcollection-literals
// PROBLEM: none
interface A
val list: Array<A?> = arrayOfNulls<caret>(3)