// COMPILER_ARGUMENTS: -Xcollection-literals
// FIX: Replace with a function call
class A {
    val x: Set<Int> get() = [1, 4<caret>]
}