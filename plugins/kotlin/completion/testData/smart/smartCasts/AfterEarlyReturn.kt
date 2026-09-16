fun test(value: Any): String {
    if (value !is String) return ""
    return val<caret>
}

// EXIST: { lookupString: "value", typeText: "String" }
