fun test() {
    Foo().foo()<caret>.condition()
}

fun Boolean.condition(): Boolean = true

class Foo {
    fun foo(): Boolean = true
}