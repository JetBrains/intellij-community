// PROBLEM: none
// WITH_STDLIB
class Foo

class Test {
    fun foo(): Any = ""

    fun bar() {}

    fun test(a: Any) {
        foo().apply {
            <caret>if (this is Foo) {
                bar()
            }
        }
    }
}
