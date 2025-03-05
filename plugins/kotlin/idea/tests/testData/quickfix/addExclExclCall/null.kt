// "Add non-null asserted (null!!) call" "false"
// ACTION: Convert to lazy property
// ACTION: Add 'toString()' call
// ACTION: Change type of 'x' to 'String?'
// ACTION: Convert property initializer to getter
// ERROR: Null can not be a value of a non-null type String
// K2_AFTER_ERROR: Null cannot be a value of a non-null type 'String'.

val x: String = null<caret>