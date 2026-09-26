class MyException : Exception()
class MyError : Error()
class MyRuntimeException : RuntimeException()

fun test() {
    throw <caret>
}

// EXIST: MyException
// EXIST: MyError
// EXIST: MyRuntimeException
