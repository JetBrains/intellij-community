// COMPILER_ARGUMENTS: -Xcollection-literals
// FIX: Replace with a function call and remove type conversion

val x = ([1, 2]).toMutab<caret>leList()