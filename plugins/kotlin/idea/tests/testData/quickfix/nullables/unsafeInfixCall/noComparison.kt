// "Replace with safe (?.) call" "false"
// ERROR: Operator call corresponds to a dot-qualified call 'p1.compareTo(p2)' which is not allowed on a nullable receiver 'p1'.
// ACTION: Add non-null asserted (p1!!) call
// ACTION: Enable option 'Local variable types' for 'Types' inlay hints
// ACTION: Expand boolean expression to 'if else'
// ACTION: Flip '>'
// ACTION: Replace overloaded operator with function call
// K2_AFTER_ERROR: Operator call is prohibited on a nullable receiver of type 'SafeType?'. Use '?.'-qualified call instead.

class SafeType {
    operator fun compareTo(other : SafeType) = 0
}
fun safeA(p1: SafeType?, p2: SafeType) {
    val v8 = p1 <caret>> p2
}
