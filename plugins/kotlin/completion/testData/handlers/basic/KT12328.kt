// FIR_COMPARISON
// FIR_IDENTICAL
fun f() {
    val v = <caret>if (x) a else b
}

// ELEMENT: emptyList