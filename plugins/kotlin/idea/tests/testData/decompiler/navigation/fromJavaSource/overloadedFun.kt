package testData.libraries

@JvmOverloads
fun <T> String.overloadedFun(vararg specs: String, allowExisting: Boolean = false, x: Int, y: Int = 2, z: T): String {
    TODO()
}
open class OpenClassWithFunctionWithDefaultParameter {
    open fun doSmth(condition: Boolean = true) {}
}

class ChildOfOpenClassWithFunctionWithDefaultParameter : OpenClassWithFunctionWithDefaultParameter() {
    override fun doSmth(condition: Boolean) {}
}

fun funWithDefaultParameter(a: Int, b: String = "abc") {}