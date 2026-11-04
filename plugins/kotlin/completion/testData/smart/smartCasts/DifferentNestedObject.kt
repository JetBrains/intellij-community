class Outer {
    object Inner {
        val value: Any = ""
        val valueText: String = ""
    }

    object Other {
        val value: Any = ""
        val valueText: String = ""
    }
}

fun test(): String {
    if (Outer.Inner.value is String) {
        return Outer.Other.val<caret>
    }
    return ""
}

// EXIST: { lookupString: "valueText", typeText: "String" }
// ABSENT: value
