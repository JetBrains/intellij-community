// WITH_STDLIB

class Foo {
    companion object Bar {
        @JvmStatic
        fun main(args: Array<String>): <caret>Int {}
    }
}