// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
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
