fun test(value: Any): String {
    return val<caret>
}

// EXIST: { lookupString: "value", typeText: "Any" }
