// IGNORE_K2
fun some(f: () -> Unit) { f() }

fun test() {
    som<caret>()
}

// ELEMENT: some
// CHAR: '\t'