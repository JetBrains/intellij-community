class Box(val valueA: Any, val valueZ: String)

fun test(first: Box, second: Box): String {
    if (first.valueA is String) {
        return second.value<caret>
    }
    return ""
}

// ORDER: valueZ
// ORDER: valueA
