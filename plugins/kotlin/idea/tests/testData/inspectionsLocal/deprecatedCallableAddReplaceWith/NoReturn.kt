// PROBLEM: none
// ERROR: A 'return' expression required in a function with a block body ('{...}')
// K2_ERROR: NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY

<caret>@Deprecated("")
fun foo(): String {
    bar()
}

fun bar(): String = ""