fun reportError(): Nothing

fun usage(a: Int?): Int {
    return when {
        a == null -> re<caret>
        else -> a
    }
}

// IGNORE_K2
// ORDER: reportError
// ORDER: return
