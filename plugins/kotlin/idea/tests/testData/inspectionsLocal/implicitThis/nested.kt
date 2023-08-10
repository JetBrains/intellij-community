// WITH_STDLIB

class Foo {
    fun s() = ""

    fun test() {
        "".apply {
            <caret>s()
        }
    }
}