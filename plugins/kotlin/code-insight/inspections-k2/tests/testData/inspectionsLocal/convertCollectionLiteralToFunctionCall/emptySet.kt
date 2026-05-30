// COMPILER_ARGUMENTS: -Xcollection-literals
// FIX: Replace with a function call
fun<T> fee() {
    val nums: Set<T> = [<caret>]
}