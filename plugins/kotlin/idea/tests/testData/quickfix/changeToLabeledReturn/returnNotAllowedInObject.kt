// "Change to 'return@mapNotNull'" "false"
// ERROR: 'return' is not allowed here
// ERROR: Null can not be a value of a non-null type Unit
// ACTION: Add 'return@mapNotNull'
// ACTION: Add braces to 'else' statement
// ACTION: Add braces to all 'if' statements
// ACTION: Convert property initializer to getter
// ACTION: Convert to lazy property
// ACTION: Enable option 'Local variable types' for 'Types' inlay hints
// ACTION: Enable option 'Property types' for 'Types' inlay hints
// ACTION: Go To Super Property
// ACTION: Introduce local variable
// WITH_STDLIB
// K2_AFTER_ERROR: 'return' is prohibited here.
// K2_AFTER_ERROR: Null cannot be a value of a non-null type 'Unit'.
interface Type {
    val value: String
}

fun main() {
    val types = (1..10).mapNotNull { c ->
        object: Type {
            override val value = if (c % 2 == 0) "Hello" else return<caret> null
        }
    }
}
