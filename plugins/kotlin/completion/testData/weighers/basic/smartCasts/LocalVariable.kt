fun test(valueA: Any, valueZ: Any): String {
    if (valueZ is String) {
        return value<caret>
    }
    return ""
}

// ORDER: valueZ
// ORDER: valueA
