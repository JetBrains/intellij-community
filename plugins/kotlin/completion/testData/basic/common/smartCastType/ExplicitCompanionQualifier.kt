class WithCompanion {
    companion object {
        val value: Any = ""
    }
}

fun test(): String {
    if (WithCompanion.Companion.value is String) {
        return WithCompanion.Companion.val<caret>
    }
    return ""
}

// EXIST: { lookupString: "value", typeText: "String" }
