// "Add 'return' expression" "false"
// ERROR: A 'return' expression required in a function with a block body ('{...}')
// K2_AFTER_ERROR: NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY
// K2_AFTER_ERROR: SYNTAX
// K2_ERROR: NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY
// K2_ERROR: SYNTAX
fun test(): Boolean {<caret>
    val x = 42