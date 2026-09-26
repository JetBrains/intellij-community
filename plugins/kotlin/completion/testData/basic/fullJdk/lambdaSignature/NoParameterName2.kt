// FIR_IDENTICAL

fun test() {
    listOf("" to 42).map { pair -> <caret> }
}

// INVOCATION_COUNT: 0
// ABSENT: { tailText: " -> "}