// FIR_IDENTICAL
// FIR_COMPARISON
fun foo() {
    val v = HashMap<List<(s: String?) -> Unit>, Set<<caret>
}

// EXIST: String
// EXIST: kotlin
// ABSENT: defaultBufferSize
// ABSENT: readLine
