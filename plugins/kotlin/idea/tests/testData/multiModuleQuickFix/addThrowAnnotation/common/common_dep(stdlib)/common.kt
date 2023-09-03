// "Add '@Throws' annotation" "true"
// IGNORE_K2
class FooException : Exception()

fun test() {
    <caret>throw FooException()
}