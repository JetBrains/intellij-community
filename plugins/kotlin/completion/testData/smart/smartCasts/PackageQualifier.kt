package sample

private val value: Any = ""

fun test(): String {
    if (sample.value is String) {
        return sample.val<caret>
    }
    return ""
}

// EXIST: { lookupString: "value", typeText: "String" }
