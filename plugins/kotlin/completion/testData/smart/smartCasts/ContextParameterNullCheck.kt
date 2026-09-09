// COMPILER_ARGUMENTS: -Xcontext-parameters

context(valueA: String?, valueZ: String?)
fun test(): String {
    if (valueZ != null) {
        return value<caret>
    }
    return ""
}

// EXIST: { lookupString: "valueZ", typeText: "String" }
// ABSENT: valueA
