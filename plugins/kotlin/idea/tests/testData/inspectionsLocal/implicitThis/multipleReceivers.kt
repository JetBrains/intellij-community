// WITH_STDLIB

class Foo {
    fun Bar.s() = ""
}

class Bar

fun test() {
    Bar().apply {
        Foo().apply apply2@ {
            <caret>s()
        }
    }
}