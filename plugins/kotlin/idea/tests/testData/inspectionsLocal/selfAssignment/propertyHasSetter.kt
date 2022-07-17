// PROBLEM: none
// WITH_STDLIB

class Test {
    var foo = 1
        set(value) {
            println(value)
        }

    fun test() {
        foo = <caret>foo
    }
}
