// LANGUAGE_VERSION: 1.6
// AFTER-WARNING: Elvis operator (?:) always returns the left operand of non-nullable type Int
// AFTER-WARNING: Parameter 'v' is never used
fun test() {
    foo(<caret>false || !true) ?: return
}
fun foo(v: Boolean): Int = 1