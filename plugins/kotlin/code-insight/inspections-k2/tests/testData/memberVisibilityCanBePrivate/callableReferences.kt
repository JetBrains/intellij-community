import kotlin.reflect.KFunction
class A {
    fun runFunction(func: KFunction<*>) {}
}

class B {
    fun <caret>myFunction() {}

    fun run(a:A) {
        a.runFunction(::myFunction)
    }
}