// "Add 'return' before the expression" "false"
// ERROR: A 'return' expression required in a function with a block body ('{...}')
// ACTION: Compiler warning 'UNUSED_EXPRESSION' options
// ACTION: Introduce local variable

fun test(): Boolean {
    <caret>5
}
