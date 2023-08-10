// WITH_STDLIB

class FooException : Exception()

class Test {
    constructor() {
        <caret>throw FooException()
    }
}