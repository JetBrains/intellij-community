// WITH_STDLIB

class Foo {
    fun test() {
        "".apply {
            <caret>this@Foo.s()
        }
    }
}

fun Foo.s() = ""