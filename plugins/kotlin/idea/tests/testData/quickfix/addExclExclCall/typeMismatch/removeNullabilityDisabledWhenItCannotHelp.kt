// "Add non-null asserted (s!!) call" "false"
// ACTION: Change parameter 's' type of function 'other' to 'String?'
// ACTION: Create function 'other'
// ACTION: Convert to also
// ACTION: Convert to apply
// ERROR: Type mismatch: inferred type is String? but Int was expected
// K2_AFTER_ERROR: Argument type mismatch: actual type is 'String?', but 'Int' was expected.
fun test() {
    val s: String? = ""
    other(<caret>s)
}

fun other(s: Int) {}