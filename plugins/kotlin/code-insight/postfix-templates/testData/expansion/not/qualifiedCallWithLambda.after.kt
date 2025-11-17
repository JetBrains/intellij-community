fun test() {
    !Foo().condition { }
}

class Foo {
    fun condition(f: () -> Unit): Boolean = true
}