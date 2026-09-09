open class Base(val valueA: Any, val valueZ: Any)

class Box(valueA: Any, valueZ: Any) : Base(valueA, valueZ) {
    fun Box.test(): String {
        if (this@test.valueA is String) {
            return value<caret>
        }
        return ""
    }
}

// EXIST: { lookupString: "valueA", typeText: "String" }
// ABSENT: valueZ
