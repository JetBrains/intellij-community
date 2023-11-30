// IGNORE_K1
// FIR_COMPARISON
// FIR_IDENTICAL
fun doHere() {
    while (true) {
        continue.<caret>
    }
}

// INVOCATION_COUNT: 1
// NUMBER: 0