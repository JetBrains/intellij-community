class Box(val valueA: Any, val valueZ: Any)

class Holder(val box: Box) {
    fun Holder.test(): String {
        if (this@test.box.valueA is String) {
            return box.value<caret>
        }
        return ""
    }
}

// EXIST: { lookupString: "valueA", typeText: "String" }
