// PROBLEM: none
// WITH_STDLIB
// ERROR: Unresolved reference: KFunction
class B {
    fun runFunction(func: KFunction<*>) {}

    fun <caret>myFunction() {}

    fun run() {
        runFunction(::myFunction)
    }
}