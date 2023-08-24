// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
interface WithInvoke {
    operator fun invoke()
}

object One {
    object Two {
        val staticVal: Any = Any()
    }
}

fun test(param: Any) {
    if (param is WithInvoke) {
        param()
    }

    if (One.Two.staticVal is WithInvoke) {
        One.Two.staticVal()
    }
}