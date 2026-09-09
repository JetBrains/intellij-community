// COMPILER_ARGUMENTS: -Xcontext-parameters

class Box(val valueA: Any, val valueZ: String)

context(first: Box, second: Box)
fun test(): String {
    if (first.valueA is String) {
        return second.value<caret>
    }
    return ""
}

// EXIST: { lookupString: "valueA", typeText: "Any" }
// EXIST: { lookupString: "valueZ", typeText: "String" }
