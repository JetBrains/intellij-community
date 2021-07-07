package testing

object O {
    fun foo() = 42
}

fun testing(): O {
    O.foo()
    val o = O
    o.foo()
    return O
}
