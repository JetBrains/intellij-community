// "Add 'return' expression" "false"
// ERROR: A 'return' expression required in a function with a block body ('{...}')
fun test(): Boolean {<caret>
    val x = 42