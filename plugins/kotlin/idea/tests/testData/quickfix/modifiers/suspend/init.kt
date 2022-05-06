// "Make bar suspend" "false"
// ACTION: Do not show return expression hints
// ACTION: Introduce import alias
// ERROR: Suspend function 'foo' should be called only from a coroutine or another suspend function

suspend fun foo() {}

class My {
    init {
        <caret>foo()
    }
}
