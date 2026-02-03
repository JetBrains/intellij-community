// SET_TRUE: METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE
// SET_TRUE: METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE
// AFTER-WARNING: Parameter 'b' is never used
// AFTER-WARNING: Parameter 'c' is never used
// AFTER-WARNING: Parameter 'p' is never used
fun foo(p: Int, c: Char, b: <caret>Boolean) {
}