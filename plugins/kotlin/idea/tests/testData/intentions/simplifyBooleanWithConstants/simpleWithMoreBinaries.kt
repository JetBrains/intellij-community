// AFTER-WARNING: Parameter 'y' is never used
// AFTER-WARNING: The expression is unused
fun foo(y: Boolean) {
    (y && false) || (y && y && true && (y && true))<caret> && false && true
}