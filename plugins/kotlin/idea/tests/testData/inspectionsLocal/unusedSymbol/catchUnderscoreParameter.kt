// PROBLEM: none
class Foo {
    fun foo() {
        try {
            println();
        } catch (_<caret>: Throwable) {
        }
    }
}