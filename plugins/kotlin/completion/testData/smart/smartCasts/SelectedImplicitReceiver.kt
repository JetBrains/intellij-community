class Box(val valueA: Any, val valueZ: Any) {
    fun Box.test(): String {
        if (this@test.valueA is String) {
            return value<caret>
        }
        return ""
    }
}

// EXIST: { lookupString: "valueA", typeText: "String" }
// ABSENT: valueZ
