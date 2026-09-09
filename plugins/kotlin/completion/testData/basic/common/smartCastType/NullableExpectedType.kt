fun test(value: Any): String? {
    if (value is String) {
        return val<caret>
    }
    return null
}

// EXIST: { lookupString: "value", typeText: "String" }
