// WITH_STDLIB
// AFTER-WARNING: Parameter 'value' is never used

class FooException : Exception()

class Test {
    var setter: String = ""
        set(value) = <caret>throw FooException()
}