// WITH_STDLIB

class Foo {
    fun test() {
        "".apply {
            <caret>this@Foo.s()
        }
    }
}

fun Foo.s() = ""

// KTIJ-32433
// IGNORE_K2