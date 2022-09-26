// FIR_IDENTICAL
// FIR_COMPARISON

class C {
    val prop: Int = 0

    fun foo() {
        this::pr<caret>
    }
}

// ELEMENT: prop
