fun value(): Any = ""

fun test(): String {
    if (value() is String) {
        return val<caret>
    }
    return ""
}

// EXIST: { lookupString: "value", typeText: "Any" }
