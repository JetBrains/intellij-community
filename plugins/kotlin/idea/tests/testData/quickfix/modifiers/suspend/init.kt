// "Make bar suspend" "false"
// ACTION: Introduce import alias
// ERROR: Suspend function 'foo' should be called only from a coroutine or another suspend function
// K2_AFTER_ERROR: ILLEGAL_SUSPEND_FUNCTION_CALL
// K2_ERROR: ILLEGAL_SUSPEND_FUNCTION_CALL

suspend fun foo() {}

class My {
    init {
        <caret>foo()
    }
}
