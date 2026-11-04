class WithCompanion {
    companion object {
        val value: Any = ""
        val valueText: String = ""
    }
}

fun test(): String {
    if (WithCompanion.value is String) {
        return WithCompanion.val<caret>
    }
    return ""
}

// EXIST: { lookupString: "value", typeText: "String" }
// EXIST: { lookupString: "valueText", typeText: "String" }
