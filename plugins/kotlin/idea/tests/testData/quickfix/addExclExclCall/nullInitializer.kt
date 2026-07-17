// "Add non-null asserted (arg!!) call" "false"
// ACTION: Add 'toString()' call
// ACTION: Change type of 'x' to 'String?'
// ERROR: Type mismatch: inferred type is String? but String was expected
// K2_AFTER_ERROR: INITIALIZER_TYPE_MISMATCH
// K2_ERROR: INITIALIZER_TYPE_MISMATCH

fun foo(arg: String?) {
    if (arg == null) {
        val x: String = arg<caret>
    }
}