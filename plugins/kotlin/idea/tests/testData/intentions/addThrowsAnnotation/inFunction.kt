// INTENTION_TEXT: "Add '@Throws' annotation"
// WITH_STDLIB

class FooException : Exception()

fun test() {
    <caret>throw FooException()
}