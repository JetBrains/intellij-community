// LANGUAGE_VERSION: 1.6
// AFTER-WARNING: The expression is unused
fun foo(y: Boolean) {
    <caret>3 != 3 && 2 > 1 || y
}