// WITH_STDLIB
// PROBLEM: none

class Foo {
    var s = ""

    fun test() {
        val s = ""
        <caret>this.s = s
    }
}