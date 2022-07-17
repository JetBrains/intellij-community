// WITH_STDLIB
class FooException : Exception()

@Throws()
fun test() {
    <caret>throw FooException()
}