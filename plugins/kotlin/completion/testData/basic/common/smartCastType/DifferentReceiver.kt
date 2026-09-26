class Box(val value: Any)

fun test(first: Box, second: Box): String {
    if (first.value is String) {
        return second.val<caret>
    }
    return ""
}

// EXIST: { lookupString: "value", typeText: "Any" }
