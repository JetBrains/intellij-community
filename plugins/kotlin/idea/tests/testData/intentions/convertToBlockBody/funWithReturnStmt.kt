// WITH_STDLIB
// K2_ERROR: Return type 'Nothing' needs to be specified explicitly.
// K2_ERROR: Return type mismatch: expected 'Nothing', actual 'String'.
// K2_ERROR: Returns are prohibited in functions with expression body. Use block body '{...}'.

fun <caret>foo() = return "42"

// IGNORE_K1
