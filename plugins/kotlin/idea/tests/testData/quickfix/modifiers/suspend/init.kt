// "Make bar suspend" "false"
// ACTION: Introduce import alias
// ERROR: Suspend function 'foo' should be called only from a coroutine or another suspend function
// K2_AFTER_ERROR: Suspend function 'suspend fun foo(): Unit' can only be called from a coroutine or another suspend function.

suspend fun foo() {}

class My {
    init {
        <caret>foo()
    }
}
