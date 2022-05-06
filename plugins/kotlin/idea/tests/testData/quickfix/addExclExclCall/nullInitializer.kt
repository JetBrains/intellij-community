// "Add non-null asserted (!!) call" "false"
// ACTION: Add 'toString()' call
// ACTION: Change type of 'x' to 'String?'
// ACTION: Do not show return expression hints
// ERROR: Type mismatch: inferred type is String? but String was expected

fun foo(arg: String?) {
    if (arg == null) {
        val x: String = arg<caret>
    }
}