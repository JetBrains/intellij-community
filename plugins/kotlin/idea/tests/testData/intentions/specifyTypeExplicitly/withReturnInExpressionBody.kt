// K2_ERROR: Return type 'Nothing' needs to be specified explicitly.
// K2_ERROR: Return type mismatch: expected 'Nothing', actual 'String'.
// K2_ERROR: Returns are prohibited in functions with expression body and without explicit return type. Use block body '{...}' or add an explicit return type.
fun m<caret>() = return "42"

// IGNORE_K1