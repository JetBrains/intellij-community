// FIR_IDENTICAL
// FIR_COMPARISON
fun bar() {
    val handler = { <caret> }
}

// INVOCATION_COUNT: 0
// EXIST: bar
// EXIST: null
