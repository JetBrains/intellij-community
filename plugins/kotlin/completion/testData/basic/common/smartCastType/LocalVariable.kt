fun test(value: Any): String {
    if (value is String) {
        return val<caret>
    }
    return ""
}

// EXIST: { lookupString: "value", typeText: "String" }
