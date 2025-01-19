// PROBLEM: none
class Foo {
    fun bar() {}

    fun foo() {
        try {
            bar()
        } catch (_<caret>: Throwable) {
        }
    }
}