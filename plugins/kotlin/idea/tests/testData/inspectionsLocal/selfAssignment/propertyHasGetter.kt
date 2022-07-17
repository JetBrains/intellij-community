// PROBLEM: none
// WITH_STDLIB

class Test {
    var foo = 1
        get() {
            println()
            return 2
        }

    fun test() {
        foo = <caret>foo
    }
}