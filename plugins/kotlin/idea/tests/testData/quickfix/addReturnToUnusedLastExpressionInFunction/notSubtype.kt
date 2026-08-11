// "Add 'return' before the expression" "false"
// ERROR: A 'return' expression required in a function with a block body ('{...}')
// ACTION: Compiler warning 'UNUSED_EXPRESSION' options
// ACTION: Introduce local variable
// K2_AFTER_ERROR: NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY
// K2_ERROR: NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY

fun test(): Boolean {
    <caret>5
}
