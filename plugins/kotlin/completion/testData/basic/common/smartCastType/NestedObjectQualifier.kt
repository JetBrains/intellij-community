class Outer {
    object Inner {
        val value: Any = ""
    }
}

fun test(): String {
    if (Outer.Inner.value is String) {
        return Outer.Inner.val<caret>
    }
    return ""
}

// EXIST: { lookupString: "value", typeText: "String" }
