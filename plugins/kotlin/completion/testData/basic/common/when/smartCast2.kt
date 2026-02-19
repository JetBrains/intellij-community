// FIR_COMPARISON
// FIR_IDENTICAL
private fun Any.test(): Int = when {
    this is String -> len<caret>
    else -> 0
}

// EXIST: length