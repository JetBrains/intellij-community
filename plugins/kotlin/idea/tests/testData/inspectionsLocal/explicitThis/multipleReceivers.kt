// WITH_STDLIB

class Foo {
    val s = ""

    fun test() {
        "".apply {
            <caret>this@Foo.s
        }
    }
}