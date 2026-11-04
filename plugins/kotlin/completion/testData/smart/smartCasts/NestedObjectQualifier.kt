class Outer {
    object Inner {
        val value: Any = ""
        val valueText: String = ""
    }
}

fun test(): String {
    if (Outer.Inner.value is String) {
        return Outer.Inner.val<caret>
    }
    return ""
}

// EXIST: { lookupString: "value", typeText: "String" }
// EXIST: { lookupString: "valueText", typeText: "String" }
