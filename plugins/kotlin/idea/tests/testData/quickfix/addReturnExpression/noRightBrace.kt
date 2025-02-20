// "Add 'return' expression" "false"
// ERROR: A 'return' expression required in a function with a block body ('{...}')
// K2_AFTER_ERROR: Missing return statement.
fun test(): Boolean {<caret>
    val x = 42