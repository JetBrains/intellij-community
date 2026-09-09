class Box(val value: Any)

fun test(box: Box): String {
    if (box.value is String) {
        return ((box)).val<caret>
    }
    return ""
}

// EXIST: { lookupString: "value", typeText: "String" }
