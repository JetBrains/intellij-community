// FIR_COMPARISON
// FIR_IDENTICAL
class C {
    inner class Inner(s: String)
}

fun foo(c: C) {
    c.<caret>
}

// ELEMENT: Inner