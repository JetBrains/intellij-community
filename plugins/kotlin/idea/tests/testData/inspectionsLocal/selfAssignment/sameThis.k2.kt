// PROBLEM: none
// WITH_STDLIB
// IGNORE_K1

class Test {
    var foo = 1

    fun test() {
        with (Test()) {
            this.foo = <caret>foo
        }
    }
}