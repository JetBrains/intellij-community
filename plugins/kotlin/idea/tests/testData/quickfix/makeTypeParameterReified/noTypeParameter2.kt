// "Make type parameter reified and function inline" "false"
// ACTION: Change type arguments to <*>
// ACTION: Convert to block body
// ACTION: Introduce local variable
// ACTION: Expand boolean expression to 'if else'
// ERROR: Cannot check for instance of erased type: List<Int>
// K2_AFTER_ERROR: CANNOT_CHECK_FOR_ERASED
// K2_ERROR: CANNOT_CHECK_FOR_ERASED
fun <T> test(a: List<Any>) = a is List<Int><caret>