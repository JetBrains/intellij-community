// FIR_IDENTICAL
// FIR_COMPARISON
fun foo(): String {
    val list = listOf(1, 2, 3)
    return "$list.<caret>"
}

// ELEMENT: []
