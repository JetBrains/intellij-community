class WithCompanion {
    companion object {
        val value: Any = ""
        val valueText: String = ""
    }
}

fun test(): String {
    if (WithCompanion.Companion.value is String) {
        return WithCompanion.Companion.val<caret>
    }
    return ""
}

// EXIST: { lookupString: "value", typeText: "String" }
// EXIST: { lookupString: "valueText", typeText: "String" }
