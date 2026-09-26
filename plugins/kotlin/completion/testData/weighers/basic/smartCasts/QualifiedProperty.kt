class Box(val valueA: Any, val valueZ: Any)

fun test(box: Box): String {
    if (box.valueZ is String) {
        return box.value<caret>
    }
    return ""
}

// ORDER: valueZ
// ORDER: valueA
