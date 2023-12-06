// "Add 'CancellationException::class'" "false"
// ERROR: @Throws must have non-empty class list
// ACTION: Make internal
// ACTION: Make private
// ACTION: Remove annotation
// IGNORE_K2
// No compilation error => no quickfix.

<caret>@Throws()
suspend fun emptyThrows() {}
