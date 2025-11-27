fun test() {
    Foo().condition { }<caret>
}

class Foo {
    fun condition(f: () -> Unit): Boolean = true
}