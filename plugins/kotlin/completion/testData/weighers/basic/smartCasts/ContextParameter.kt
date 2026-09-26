// COMPILER_ARGUMENTS: -Xcontext-parameters

context(valueA: Any, valueZ: Any)
fun test(): String {
    if (valueZ is String) {
        return value<caret>
    }
    return ""
}

// ORDER: valueZ
// ORDER: valueA
