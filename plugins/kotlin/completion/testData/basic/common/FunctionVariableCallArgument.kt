// FIR_IDENTICAL
// FIR_COMPARISON
fun f(p: (String) -> Unit, s: String) {
    p(<caret>)
}

// EXIST: s
// EXIST: StringBuilder
