// COMPILER_ARGUMENTS: -Xcollection-literals
// FIX: Replace with a function call
fun test(): List<String> {
    return [<caret>"a", "b", "c"]
}