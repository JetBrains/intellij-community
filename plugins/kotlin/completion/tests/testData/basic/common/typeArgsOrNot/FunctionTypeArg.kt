// FIR_IDENTICAL
// FIR_COMPARISON
fun foo() {
    val v = listOf<<caret>
}

// EXIST: String
// EXIST: kotlin
// ABSENT: defaultBufferSize
// ABSENT: readLine
