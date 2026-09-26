// "Make bar suspend" "false"
// ACTION: Convert property initializer to getter
// ACTION: Convert to lazy property
// ACTION: Enable option 'Property types' for 'Types' inlay hints
// ACTION: Introduce import alias
// ERROR: Suspend function 'foo' should be called only from a coroutine or another suspend function
// K2_AFTER_ERROR: ILLEGAL_SUSPEND_FUNCTION_CALL
// K2_ERROR: ILLEGAL_SUSPEND_FUNCTION_CALL

suspend fun foo() = 42
val x = <caret>foo()
