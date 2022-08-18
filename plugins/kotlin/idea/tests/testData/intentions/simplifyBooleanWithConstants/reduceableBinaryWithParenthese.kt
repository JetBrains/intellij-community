// LANGUAGE_VERSION: 1.6
// AFTER-WARNING: Parameter 'y' is never used
fun foo(y: Boolean) {
    val x = 3
    <caret>x != x && (2 > 1 || y)
}