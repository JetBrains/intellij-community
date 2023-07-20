// FIR_IDENTICAL
// FIR_COMPARISON
fun some(f: () -> Unit) { f() }

fun test() {
    som<caret>()
}

// ELEMENT: some
// CHAR: '\t'