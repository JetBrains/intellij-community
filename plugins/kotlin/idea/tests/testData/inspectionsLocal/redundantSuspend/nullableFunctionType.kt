// IGNORE_FE10_BINDING_BY_FIR
// PROBLEM: none
class Test(private val fn: (suspend (Int) -> String)?) {
    <caret>suspend fun invokeFunction(x: Int): String = fn!!(x)
}