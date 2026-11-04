object Holder {
    val value: Any = ""
    val valueText: String = ""
}

fun test(): String {
    if (Holder.value is String) {
        return Holder.val<caret>
    }
    return ""
}

// EXIST: { lookupString: "value", typeText: "String" }
// EXIST: { lookupString: "valueText", typeText: "String" }
