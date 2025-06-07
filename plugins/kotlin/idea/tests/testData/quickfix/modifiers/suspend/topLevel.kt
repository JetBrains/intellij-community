// "Make bar suspend" "false"
// ACTION: Convert property initializer to getter
// ACTION: Convert to lazy property
// ACTION: Enable option 'Property types' for 'Types' inlay hints
// ACTION: Introduce import alias
// ERROR: Suspend function 'foo' should be called only from a coroutine or another suspend function
// K2_AFTER_ERROR: Suspend function 'suspend fun foo(): Int' can only be called from a coroutine or another suspend function.

suspend fun foo() = 42
val x = <caret>foo()
