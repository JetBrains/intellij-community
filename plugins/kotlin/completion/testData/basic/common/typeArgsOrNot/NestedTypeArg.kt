// FIR_IDENTICAL
// FIR_COMPARISON
fun foo() {
    val v = HashMap<List<(s: String?) -> Unit>, Set<<caret>
}

// EXIST: String
// EXIST_K2: String
// EXIST: kotlin
// EXIST_K2: kotlin.
// ABSENT: defaultBufferSize
// ABSENT: readLine
