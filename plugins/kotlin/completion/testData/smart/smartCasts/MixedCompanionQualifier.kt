class WithCompanion {
    companion object {
        val value: Any = ""
        val valueText: String = ""
    }
}

fun test(): String {
    if (WithCompanion.value is String) {
        return WithCompanion.Companion.val<caret>
    }
    return ""
}

// EXIST: { lookupString: "valueText", typeText: "String" }
// ABSENT: value
