// COMPILER_ARGUMENTS: -Xcontext-parameters

context(valueA: Any, valueZ: Any)
val text: String
    get() {
        if (valueZ is String) {
            return value<caret>
        }
        return ""
    }

// ORDER: valueZ
// ORDER: valueA
