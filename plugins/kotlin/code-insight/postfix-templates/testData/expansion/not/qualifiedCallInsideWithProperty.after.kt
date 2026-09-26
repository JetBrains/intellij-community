fun test() {
    (!Foo().foo).condition()
}

fun Boolean.condition(): Boolean = true

class Foo {
    val foo: Boolean = true
}