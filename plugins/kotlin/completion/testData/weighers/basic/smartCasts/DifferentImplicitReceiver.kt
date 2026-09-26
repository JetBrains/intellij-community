class Box(val valueA: Any, val valueZ: String) {
    fun Box.test(): String {
        if (this@Box.valueA is String) {
            return value<caret>
        }
        return ""
    }
}

// ORDER: valueZ
// ORDER: valueA
