class Box(val value: Any)

fun Box.test(valueText: String): String {
    if (value is String) {
        return val<caret>
    }
    return valueText
}

// EXIST: { lookupString: "value", typeText: "String" }
// EXIST: { lookupString: "valueText", typeText: "String" }
