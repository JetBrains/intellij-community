// K2_ERROR: Property type 'Nothing' needs to be specified explicitly.
// K2_ERROR: Return type mismatch: expected 'Nothing', actual 'String'.
// K2_ERROR: Returns are prohibited in functions with expression body and without explicit return type. Use block body '{...}' or add an explicit return type.
val p
    g<caret>et() = return "42"

// IGNORE_K1