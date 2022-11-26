fun test() {
    !(Foo().condition())
}

class Foo {
    fun condition(): Boolean = true
}