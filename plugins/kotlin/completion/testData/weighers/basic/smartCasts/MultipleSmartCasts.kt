fun test(valueA: Any, valueY: Any, valueZ: Any): String {
    if (valueY is String && valueZ is String) {
        return value<caret>
    }
    return ""
}

// ORDER: valueY
// ORDER: valueZ
// ORDER: valueA
