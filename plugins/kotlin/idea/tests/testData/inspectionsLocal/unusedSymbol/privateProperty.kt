// WITH_STDLIB

class Foo {
    private val foo = object {
        var bar<caret>: Int = 42
    }
}