class Box(val valueA: Any, val valueZ: Any) {
    fun String.test(): String {
        if (this@Box.valueA is String) {
            return value<caret>
        }
        return ""
    }
}

// EXIST: { lookupString: "valueA", typeText: "String" }
// ABSENT: valueZ
