// FIR_IDENTICAL
// FIR_COMPARISON
class C {
    fun `1_fun`() {}
}

fun main() {
    C().<caret>
}

// ELEMENT: 1_fun