fun foo() {
    val v = HashMap<String, Buf<caret>
}

// FIR_COMPARISON
// FIR_IDENTICAL
// ELEMENT: BufferedReader