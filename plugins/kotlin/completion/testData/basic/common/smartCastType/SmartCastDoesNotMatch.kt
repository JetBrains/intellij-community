fun test(value: Any): Int {
    if (value is String) {
        return val<caret>
    }
    return 0
}

// EXIST: { lookupString: "value", typeText: "Any" }
