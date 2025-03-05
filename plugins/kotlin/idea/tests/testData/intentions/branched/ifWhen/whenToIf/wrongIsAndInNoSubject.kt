// PRIORITY: LOW
// ERROR: Expected condition of type Boolean
// ERROR: Expected condition of type Boolean
// SKIP_ERRORS_AFTER
// K2_ERROR: Condition of type 'Boolean' expected.
// K2_ERROR: Condition of type 'Boolean' expected.
// K2_ERROR: Type inference failed. The value of the type parameter 'T' must be mentioned in input types (argument types, receiver type, or expected type). Try to specify it explicitly.

fun test(n: Int): String {
    return <caret>when {
        is String -> "String"
        in 1..10 -> "1..10"
        else -> "unknown"
    }
}