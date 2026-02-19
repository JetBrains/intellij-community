fun <U, V> test() {}
fun foo() {
    test<String, Buf<caret>
}

// FIR_COMPARISON
// FIR_IDENTICAL
// ELEMENT: BufferedReader