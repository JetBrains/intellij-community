// "Change to 'return@mapNotNull'" "false"
// ERROR: 'return' is not allowed here
// ERROR: Null can not be a value of a non-null type Unit
// ACTION: Add braces to 'else' statement
// ACTION: Add braces to all 'if' statements
// ACTION: Convert property initializer to getter
// ACTION: Convert to lazy property
// ACTION: Enable option 'Local variable types' for 'Types' inlay hints
// ACTION: Enable option 'Property types' for 'Types' inlay hints
// ACTION: Move to constructor
// WITH_STDLIB
// K2_AFTER_ERROR: NULL_FOR_NONNULL_TYPE
// K2_AFTER_ERROR: RETURN_NOT_ALLOWED
// K2_ERROR: NULL_FOR_NONNULL_TYPE
// K2_ERROR: RETURN_NOT_ALLOWED
fun main() {
    val types = (1..10).mapNotNull { c ->
        class Type {
            val value = if (c % 2 == 0) "Hello" else return<caret> null
        }
        Type()
    }
}
