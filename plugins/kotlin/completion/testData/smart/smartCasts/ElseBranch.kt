fun test(value: Any): String {
    if (value !is String) {
        return ""
    } else {
        return val<caret>
    }
}

// EXIST: { lookupString: "value", typeText: "String" }
