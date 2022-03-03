// WITH_STDLIB
// PROBLEM: none

class Foo {
    val s = ""

    fun test() {
        Bar().apply {
            "".run {
                <caret>this@apply.s
            }
        }
    }
}

class Bar {
    val s = ""
}