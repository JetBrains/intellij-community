// COMPILER_ARGUMENTS: -Xcollection-literals
// FIX: Replace with a function call

val map = buildMap {
    getOrPut("key") { [1, 2,<caret> 4] }
}