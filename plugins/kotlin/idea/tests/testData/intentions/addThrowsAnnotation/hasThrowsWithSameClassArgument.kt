// WITH_STDLIB
// IS_APPLICABLE: false

class FooException : Exception()

@Throws(FooException::class)
fun test() {
    <caret>throw FooException()
}