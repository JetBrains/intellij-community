// "Convert to 'isArrayOf' call" "false"
// ACTION: Add full qualifier
// ACTION: Change type arguments to <*>
// ACTION: Expand boolean expression to 'if else'
// ACTION: Introduce import alias
// ERROR: Cannot check for instance of erased type: List<String>
// K2_AFTER_ERROR: Cannot check for instance of erased type 'List<String>'.
// WITH_STDLIB
fun test(a: Any) {
    if (a is <caret>List<String>) {
    }
}