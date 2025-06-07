// FIR_IDENTICAL

fun test() {
    listOf("" to 42).map {
        if (true) {
            string<caret>
        }
    }
}

// INVOCATION_COUNT: 0
// ABSENT: { tailText: " -> " }