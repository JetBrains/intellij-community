// FIR_IDENTICAL
// FIR_COMPARISON
import A.xxx

object A {
    fun xxx() {}
}

fun test() {
    xx<caret>
}

// ELEMENT_TEXT: "xxx"