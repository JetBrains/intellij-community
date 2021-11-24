// WITH_STDLIB

class FooException : Exception()

class Test {
    val getter: String
        get() = <caret>throw FooException()
}