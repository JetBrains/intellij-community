// FIR_COMPARISON
// FIR_IDENTICAL
private fun Any.test(): Int = when {
    this !is String || len<caret> -> 1
    else -> 0
}

// EXIST: length