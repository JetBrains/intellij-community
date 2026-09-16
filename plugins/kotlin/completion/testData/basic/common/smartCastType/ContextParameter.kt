// COMPILER_ARGUMENTS: -Xcontext-parameters

context(valueA: Any, valueZ: Any)
fun test(): String {
    if (valueZ is String) {
        return value<caret>
    }
    return ""
}

// EXIST: { lookupString: "valueZ", typeText: "String" }
// EXIST: { lookupString: "valueA", typeText: "Any" }
