fun test(valueA: String?, valueZ: String?): String {
    if (valueZ != null) {
        return value<caret>
    }
    return ""
}

// ORDER: valueZ
// ORDER: valueA
