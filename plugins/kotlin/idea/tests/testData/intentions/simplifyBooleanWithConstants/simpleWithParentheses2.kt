// AFTER-WARNING: The expression is unused
fun foo(y: Boolean) {
    y || <caret>(y && true)
}