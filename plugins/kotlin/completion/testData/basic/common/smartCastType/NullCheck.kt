fun test(value: String?): String {
    if (value != null) {
        return val<caret>
    }
    return ""
}

// EXIST: { lookupString: "value", typeText: "String" }
