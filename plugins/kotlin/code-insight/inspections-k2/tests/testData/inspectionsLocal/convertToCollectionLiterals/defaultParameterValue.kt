// COMPILER_ARGUMENTS: -Xcollection-literals
fun foo(items: MutableList<Int> = mutableListOf<caret>()) {}