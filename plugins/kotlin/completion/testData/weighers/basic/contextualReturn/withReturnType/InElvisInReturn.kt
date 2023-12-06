fun reportError(): Nothing

fun usage(a: Int?): Int {
    return a ?: a ?: a ?: re<caret>
}

// IGNORE_K2
// ORDER: reportError
// ORDER: return
