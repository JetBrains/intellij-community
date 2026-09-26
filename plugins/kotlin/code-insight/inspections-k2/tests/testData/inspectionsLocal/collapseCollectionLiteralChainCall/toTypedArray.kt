// COMPILER_ARGUMENTS: -Xcollection-literals
// FIX: Replace with a function call and remove type conversion
val foo = [1, 2].toTyped<caret>Array()
