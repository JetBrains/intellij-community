// PROBLEM: none
// WITH_STDLIB

class Bar {
    var foo: Int = 1

    fun bar(): Bar = Bar()

    fun test() {
        bar().foo = <caret>bar().foo
    }
}