class Box(var value: Any, val valueText: String)

fun test(box: Box): String {
    if (box.value is String) {
        return box.val<caret>
    }
    return ""
}

// EXIST: { lookupString: "valueText", typeText: "String" }
// ABSENT: value
