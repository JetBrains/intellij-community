// COMPILER_ARGUMENTS: -Xcollection-literals
// FIX: Replace with a function call and remove type conversion
class A {
    val x: Set<Int> get() = [1, 4].to<caret>Set()
}