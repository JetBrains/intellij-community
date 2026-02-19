// "Replace with safe (?.) call" "false"
// ACTION: Add non-null asserted (w?.x!!) call
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Expand boolean expression to 'if else'
// ACTION: Flip '<='
// ACTION: Replace overloaded operator with function call
// ERROR: Operator call corresponds to a dot-qualified call 'w?.x.compareTo(42)' which is not allowed on a nullable receiver 'w?.x'.
// K2_AFTER_ERROR: Operator call is prohibited on a nullable receiver of type 'Int?'. Use '?.'-qualified call instead.

class Wrapper(val x: Int)

fun test(w: Wrapper?) {
    when {
        w?.x <caret><= 42 -> {}
    }
}
