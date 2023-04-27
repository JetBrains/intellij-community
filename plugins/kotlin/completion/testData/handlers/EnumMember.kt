// FIR_COMPARISON
// FIR_IDENTICAL
enum class E {
    A
    B
    C
}

fun foo() {
    val e = E.<caret>
}

// ELEMENT: A