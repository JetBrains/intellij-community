// LANGUAGE_VERSION: 1.1
// IS_APPLICABLE: true
// WITH_STDLIB
// AFTER-WARNING: Parameter 's' is never used
// AFTER-WARNING: Variable 'f' is never used

class Test {
    fun test() {
        with(Any()) {
            val f = { s: String<caret> -> foo(s) }
        }
    }
    fun foo(s: String) {}
}