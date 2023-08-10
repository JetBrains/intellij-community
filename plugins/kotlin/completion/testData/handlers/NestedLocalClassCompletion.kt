// FIR_COMPARISON
// FIR_IDENTICAL
fun foo() {
    class LocalClass {
        inner class Nested {}

        val v: <caret>
    }
}

// ELEMENT: Nested