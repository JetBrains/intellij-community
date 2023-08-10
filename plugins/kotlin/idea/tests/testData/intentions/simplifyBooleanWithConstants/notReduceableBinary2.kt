// LANGUAGE_VERSION: 1.6
// AFTER-WARNING: Parameter 'v' is never used
fun test() {
    foo(false || !true) + foo(<caret>false || !true)
}
fun foo(v: Boolean): Int = 1