// WITH_STDLIB

class Foo {
    private val foo = object {
        fun bar<caret>(): Int = 42
    }
}