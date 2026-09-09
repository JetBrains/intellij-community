class Box(val value: Any) {
    fun test(): String {
        if (this.value is String) {
            return this.val<caret>
        }
        return ""
    }
}

// EXIST: { lookupString: "value", typeText: "String" }
