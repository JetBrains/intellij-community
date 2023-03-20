// FIR_IDENTICAL
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