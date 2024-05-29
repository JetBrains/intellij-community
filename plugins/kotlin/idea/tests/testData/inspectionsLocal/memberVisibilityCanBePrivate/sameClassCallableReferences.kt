// PROBLEM: none
class B {
    fun runFunction(func: KFunction<*>) {}

    fun <caret>myFunction() {}

    fun run() {
        runFunction(::myFunction)
    }
}