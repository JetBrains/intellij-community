class Box(val valueA: Any, val valueZ: String) {
    fun Box.test(): String {
        if (this@Box.valueA is String) {
            return value<caret>
        }
        return ""
    }
}

// EXIST: { lookupString: "valueA", typeText: "Any" }
