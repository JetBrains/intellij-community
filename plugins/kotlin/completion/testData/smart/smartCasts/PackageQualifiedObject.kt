package sample

object Holder {
    val value: Any = ""
    val valueText: String = ""
}

fun test(): String {
    if (sample.Holder.value is String) {
        return sample.Holder.val<caret>
    }
    return ""
}

// EXIST: { lookupString: "value", typeText: "String" }
// EXIST: { lookupString: "valueText", typeText: "String" }
