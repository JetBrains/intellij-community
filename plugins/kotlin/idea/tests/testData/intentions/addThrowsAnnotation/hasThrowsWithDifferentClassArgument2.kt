// WITH_STDLIB
// DISABLE_ERRORS
class FooException : Exception()

class BarException : Exception()

@Throws(exceptionClasses = BarException::class)
fun test() {
    <caret>throw FooException()
}