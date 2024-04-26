// PROBLEM: none
// WITH_STDLIB
// IGNORE_K2

class Test {
    var foo = 1

    fun test() {
        with (Test()) {
            this@Test.foo = <caret>foo // Different receiver
        }
    }
}