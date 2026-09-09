fun test(value: Any, valueText: String): String {
    if (value is String) {
        println(value)
    }
    return val<caret>
}

// EXIST: { lookupString: "valueText", typeText: "String" }
// ABSENT: value
