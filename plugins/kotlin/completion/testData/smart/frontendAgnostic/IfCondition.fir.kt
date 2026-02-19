// FIR_COMPARISON
// REGISTRY: kotlin.k2.smart.completion.enabled true
fun bar(b: Boolean, c: Char) {
    if (<caret>
}

// EXIST: b
// ABSENT: c
// EXIST: true
// EXIST: false
