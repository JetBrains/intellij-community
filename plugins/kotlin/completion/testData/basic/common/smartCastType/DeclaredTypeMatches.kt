fun test(value: Any): Any {
    if (value is String) {
        return val<caret>
    }
    return ""
}

// EXIST: { lookupString: "value", typeText: "Any" }
