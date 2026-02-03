// WITH_STDLIB
// PROBLEM: none

class Foo {
    val s = ""

    fun test() {
        Foo().apply {
            <caret>this@Foo.s
        }
    }
}