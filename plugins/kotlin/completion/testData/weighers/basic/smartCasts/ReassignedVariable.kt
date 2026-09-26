fun test(input: Any, valueZ: String): String {
    var valueA = input
    if (valueA is String) {
        valueA = 42
        return value<caret>
    }
    return ""
}

// ORDER: valueZ
// ORDER: valueA
