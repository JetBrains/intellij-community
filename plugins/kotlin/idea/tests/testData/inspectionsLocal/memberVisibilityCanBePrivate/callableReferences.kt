// PROBLEM: none
class A {
    fun runFunction(func: KFunction<*>) {}
}

class B {
    fun <caret>myFunction() {}

    fun run() {
        runFunction(::myFunction)
    }
}