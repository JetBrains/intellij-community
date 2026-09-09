class Box(val valueA: Any, val valueZ: Any) {
    fun Box.test(): String {
        if (this@test.valueA is String) {
            return value<caret>
        }
        return ""
    }
}

// ORDER: valueA
// ORDER: valueZ
