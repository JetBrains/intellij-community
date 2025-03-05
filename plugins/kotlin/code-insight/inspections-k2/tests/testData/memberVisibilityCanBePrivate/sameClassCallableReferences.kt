import kotlin.reflect.KFunction
class B {
    private fun runFunction(func: KFunction<*>) {}

    fun <caret>myFunction() {}

    fun run() {
        runFunction(::myFunction)
    }
}