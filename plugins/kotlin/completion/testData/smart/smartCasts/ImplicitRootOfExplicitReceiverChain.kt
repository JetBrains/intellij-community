class Box(val value: Any)

class Holder(val box: Box) {
    fun test(): String {
        if (box.value is String) {
            return box.val<caret>
        }
        return ""
    }
}

// EXIST: { lookupString: "value", typeText: "String" }
