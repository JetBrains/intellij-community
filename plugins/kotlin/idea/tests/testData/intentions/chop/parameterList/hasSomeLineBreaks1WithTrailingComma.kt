// SET_TRUE: ALLOW_TRAILING_COMMA
// AFTER-WARNING: Parameter 'b' is never used
// AFTER-WARNING: Parameter 'c' is never used
// AFTER-WARNING: Parameter 'p' is never used

fun foo(p: Int, c: Char,
        b: <caret>Boolean) {
}