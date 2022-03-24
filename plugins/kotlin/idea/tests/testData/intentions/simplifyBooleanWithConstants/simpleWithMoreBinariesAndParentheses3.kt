// AFTER-WARNING: The expression is unused
fun foo(y: Boolean) {
    ((y && true) || false) <caret>&& (true && (y && (y && (y ||false))))
}