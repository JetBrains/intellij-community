fun test() {
    Foo().condition()<caret>
}

class Foo {
    fun condition(): Boolean = true
}