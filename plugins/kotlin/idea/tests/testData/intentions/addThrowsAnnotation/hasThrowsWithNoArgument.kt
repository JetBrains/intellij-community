// WITH_STDLIB
class FooException : Exception()

@Throws
fun test() {
    throw FooException()<caret>
}