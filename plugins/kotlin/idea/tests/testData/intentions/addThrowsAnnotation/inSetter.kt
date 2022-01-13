// WITH_RUNTIME
// AFTER-WARNING: Parameter 'value' is never used

class FooException : Exception()

class Test {
    var setter: String = ""
        set(value) = <caret>throw FooException()
}