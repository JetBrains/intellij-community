// PROBLEM: Variable 'foo' is assigned to itself
// WITH_STDLIB
// FIX: Remove self assignment
// IGNORE_K2

class Test {
    var foo = 1

    fun test() {
        with (Test()) {
            this.foo = <caret>foo
        }
    }
}