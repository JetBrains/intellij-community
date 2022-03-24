// WITH_STDLIB
class FooException : Exception()

class BarException : Exception()

@Throws(exceptionClasses = [BarException::class])
fun test() {
    <caret>throw FooException()
}