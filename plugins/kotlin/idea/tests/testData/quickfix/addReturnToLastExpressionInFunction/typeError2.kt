// "Add 'return' to last expression" "false"
// WITH_STDLIB
// ACTION: Add 'return' expression
// ACTION: Remove explicitly specified return type of enclosing function 'some'
// ERROR: A 'return' expression required in a function with a block body ('{...}')
// ERROR: Unresolved reference: FunctionReference
// K2_ERROR: NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY
// K2_ERROR: UNRESOLVED_REFERENCE

fun some(): Any {
    FunctionReference::class
}<caret>

// IGNORE_K2