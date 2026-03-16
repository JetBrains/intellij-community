fun test() {
    (!Foo().foo()).condition()
}

fun Boolean.condition(): Boolean = true

class Foo {
    fun foo(): Boolean = true
}