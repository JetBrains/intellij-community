// ERROR: A 'return' expression required in a function with a block body ('{...}')
// PROBLEM: none
// K2_ERROR: NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY
fun doSomething() {}

fun test(): String {
    <caret>try {
        return "success"
    } catch (e: Exception) {
        doSomething()
    }
}