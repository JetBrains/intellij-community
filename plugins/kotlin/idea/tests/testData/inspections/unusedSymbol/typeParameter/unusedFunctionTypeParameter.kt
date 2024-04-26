fun <T> unusedFunctionTypeParameter(p: String) {
    println(p)
}

fun main(args: Array<String>) {
    println(args)
    unusedFunctionTypeParameter("")
}

open class Fff {
    open fun <T> d2353() {
        D15().d2353<T>()
    }
}

class D15 : Fff() {
    override fun <T> d2353() { // must not highlght unused type param in overridden fun
        Fff().d2353<T>()
    }
}