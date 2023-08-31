// FIR_IDENTICAL
fun reportError(): Nothing

fun usage(a: Int?): Int {
    return if (a == null) re<caret> else a
}

// IGNORE_K2
// ORDER: reportError
// ORDER: return
