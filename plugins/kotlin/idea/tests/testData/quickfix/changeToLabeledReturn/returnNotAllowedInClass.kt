// "Change to 'return@mapNotNull'" "false"
// ERROR: 'return' is not allowed here
// ERROR: Null can not be a value of a non-null type Unit
// ACTION: Add braces to 'else' statement
// ACTION: Add braces to all 'if' statements
// ACTION: Convert property initializer to getter
// ACTION: Convert to lazy property
// ACTION: Move to constructor
// WITH_STDLIB
fun main() {
    val types = (1..10).mapNotNull { c ->
        class Type {
            val value = if (c % 2 == 0) "Hello" else return<caret> null
        }
        Type()
    }
}
