// "Change type arguments to <*>" "false"
// ACTION: Add full qualifier
// ACTION: Convert to 'isArrayOf' call
// ACTION: Expand boolean expression to 'if else'
// ERROR: Cannot check for instance of erased type: Array<String>
// K2_AFTER_ERROR: CANNOT_CHECK_FOR_ERASED
// K2_ERROR: CANNOT_CHECK_FOR_ERASED
fun foo(a: Any) {
    if (a is <caret>Array<String>) {
    }
}