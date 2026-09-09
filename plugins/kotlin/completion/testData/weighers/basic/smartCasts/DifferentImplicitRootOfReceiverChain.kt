class Box(val valueA: Any, val valueZ: String)

class Holder(val box: Box) {
    fun Holder.test(): String {
        if (this@Holder.box.valueA is String) {
            return box.value<caret>
        }
        return ""
    }
}

// ORDER: valueZ
// ORDER: valueA
