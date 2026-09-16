fun test(input: Any, valueText: String): String {
    var value = input
    if (value is String) {
        value = 42
        return val<caret>
    }
    return ""
}

// EXIST: { lookupString: "valueText", typeText: "String" }
// ABSENT: value
