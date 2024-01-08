// PROBLEM: Variable 'foo' is assigned to itself
// WITH_STDLIB
// FIX: Remove self assignment
// IGNORE_K1

class Test {
    var foo = 1

    fun test() {
        with (Test()) {
            this@Test.foo = <caret>foo // Different receiver
        }
    }
}