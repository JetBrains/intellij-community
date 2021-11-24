// DISABLE-ERRORS
// WITH_STDLIB
// IS_APPLICABLE: false

class FooException : Exception()

class BarException : Exception()

@Throws(exceptionClasses = listOf(BarException::class))
fun test() {
    <caret>throw FooException()
}