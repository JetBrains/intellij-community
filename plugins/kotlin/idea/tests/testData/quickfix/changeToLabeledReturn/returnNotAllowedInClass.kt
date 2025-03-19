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
// K2_AFTER_ERROR: 'return' is prohibited here.
// K2_AFTER_ERROR: Null cannot be a value of a non-null type 'Unit'.
fun main() {
    val types = (1..10).mapNotNull { c ->
        class Type {
            val value = if (c % 2 == 0) "Hello" else return<caret> null
        }
        Type()
    }
}
