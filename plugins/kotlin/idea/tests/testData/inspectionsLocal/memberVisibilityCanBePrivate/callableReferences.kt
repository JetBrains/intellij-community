// PROBLEM: none
// WITH_STDLIB
// ERROR: Unresolved reference: KFunction
// ERROR: Unresolved reference: runFunction

class A {
    fun runFunction(func: KFunction<*>) {}
}

class B {
    fun <caret>myFunction() {}

    fun run() {
        runFunction(::myFunction)
    }
}