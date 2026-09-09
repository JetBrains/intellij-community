class Box(val valueA: Any, val valueZ: Any)

class Holder(val box: Box) {
    fun Holder.test(): String {
        if (this@Holder.box.valueA is String) {
            return this@Holder.box.value<caret>
        }
        return ""
    }
}

// EXIST: { lookupString: "valueA", typeText: "String" }
// ABSENT: valueZ
