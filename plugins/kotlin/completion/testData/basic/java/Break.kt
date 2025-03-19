// IGNORE_K1
// FIR_COMPARISON
// FIR_IDENTICAL
fun doHere() {
    while (true) {
        break.<caret>
    }
}

// INVOCATION_COUNT: 1
// NUMBER: 0