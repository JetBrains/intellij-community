class Box<T>(val value: T)

fun test(box: Box<Any>): String {
    if (box.value is String) {
        return box.val<caret>
    }
    return ""
}

// EXIST: { lookupString: "value", typeText: "String" }
