// COMPILER_ARGUMENTS: -Xcollection-literals
// FIX: Replace with a function call
fun test() {
    [<caret>"aaa", "bb", "c"].toString()
}
