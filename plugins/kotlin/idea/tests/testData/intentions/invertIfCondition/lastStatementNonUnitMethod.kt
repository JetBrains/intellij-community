// ERROR: A 'return' expression required in a function with a block body ('{...}')
// K2_AFTER_ERROR: NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY
// K2_ERROR: NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY

fun foo(): Int {
    val x = 2
    <caret>if (x > 1) {
        bar()
    }
}

fun bar(){}