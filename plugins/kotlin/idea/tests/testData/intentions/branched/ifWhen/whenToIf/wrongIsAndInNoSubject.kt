// PRIORITY: LOW
// ERROR: Expected condition of type Boolean
// ERROR: Expected condition of type Boolean
// SKIP_ERRORS_AFTER
// K2_ERROR: Condition of type 'Boolean' expected.
// K2_ERROR: Condition of type 'Boolean' expected.

fun test(n: Int): String {
    return <caret>when {
        is String -> "String"
        in 1..10 -> "1..10"
        else -> "unknown"
    }
}