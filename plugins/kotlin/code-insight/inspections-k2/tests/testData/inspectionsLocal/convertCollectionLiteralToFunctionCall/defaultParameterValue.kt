// COMPILER_ARGUMENTS: -Xcollection-literals
// FIX: Replace with a function call
fun foo(items: MutableList<Int> = [<caret>]) {}
