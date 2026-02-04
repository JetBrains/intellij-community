// FIX: Change return type to Unit
// DISABLE_ERRORS
fun main(args: Array<String>): <caret>Int = 1

// For K1, the function name is highlighted instead of the return type
// IGNORE_K1