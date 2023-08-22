// LANGUAGE_VERSION: 1.1
// WITH_STDLIB
// AFTER-WARNING: Parameter 'f' is never used
fun foo(f: () -> Unit) {}

class Bar {
    fun bar() {}
}

class Test {
    fun test() {
        Bar().run {
            foo { <caret>bar() }
        }
    }
}
