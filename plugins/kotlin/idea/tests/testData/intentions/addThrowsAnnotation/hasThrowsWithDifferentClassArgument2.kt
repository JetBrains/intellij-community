// WITH_STDLIB
// DISABLE-ERRORS
class FooException : Exception()

class BarException : Exception()

@Throws(exceptionClasses = BarException::class)
fun test() {
    <caret>throw FooException()
}