// COMPILER_ARGUMENTS: -Xcontext-parameters

class Box(val valueA: Any, val valueZ: String) {
    context(other: Box)
    fun test(): String {
        if (other.valueA is String) {
            return value<caret>
        }
        return ""
    }
}

// EXIST: { lookupString: "valueZ", typeText: "String" }
// ABSENT: valueA
