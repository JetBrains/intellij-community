// "Replace with safe (?.) call" "false"
// ACTION: Add non-null asserted (w?.x!!) call
// ACTION: Expand boolean expression to 'if else'
// ACTION: Flip '>='
// ACTION: Replace overloaded operator with function call
// ERROR: Operator call corresponds to a dot-qualified call 'w?.x.compareTo(42)' which is not allowed on a nullable receiver 'w?.x'.
// K2_AFTER_ERROR: UNSAFE_OPERATOR_CALL
// K2_ERROR: UNSAFE_OPERATOR_CALL

class Wrapper(val x: Int)

fun test(w: Wrapper?) {
    while (w?.x <caret>>= 42) {}
}
