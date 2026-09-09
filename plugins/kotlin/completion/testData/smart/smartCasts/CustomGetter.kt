class Box(val valueText: String) {
    val value: Any
        get() = ""

    fun test(): String {
        if (value is String) {
            return val<caret>
        }
        return ""
    }
}

// EXIST: { lookupString: "valueText", typeText: "String" }
// ABSENT: value
