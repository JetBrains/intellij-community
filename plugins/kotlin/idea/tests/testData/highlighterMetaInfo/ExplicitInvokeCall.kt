// FIR_IDENTICAL
interface FunctionLike {
    operator fun invoke() {
    }
}

var global : () -> Unit = {}

val Int.ext : () -> Unit
    get() {
        return {}
    }

fun foo(a : () -> Unit, functionLike: FunctionLike) {
    a.invoke()
    functionLike.invoke()
    global.invoke()
    1.ext.invoke();

    {}.invoke()
}
