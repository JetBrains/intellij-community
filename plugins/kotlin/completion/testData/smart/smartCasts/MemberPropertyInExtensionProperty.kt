class Box(val value: Any)

val Box.text: String
    get() {
        if (value is String) {
            return val<caret>
        }
        return ""
    }

// EXIST: { lookupString: "value", typeText: "String" }
