// LANGUAGE_VERSION: 1.6
// AFTER-WARNING: The expression is unused
fun foo(y: Boolean) {
    <caret>2 > 1 && y || y || (3 + 3 > 10)
}