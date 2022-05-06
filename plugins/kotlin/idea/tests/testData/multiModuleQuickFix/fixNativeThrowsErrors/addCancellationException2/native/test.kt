// "Add 'CancellationException::class'" "false"
// ERROR: @Throws on suspend declaration must have CancellationException (or any of its superclasses) listed
// ACTION: Do not show return expression hints
// ACTION: Make internal
// ACTION: Make private

class MyException : Throwable()

// Quickfix doesn't support this case:
<caret>@Throws(exceptionClasses = [MyException::class])
suspend fun addCE() {}