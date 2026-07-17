// PRIORITY: LOW
// ERROR: Expected condition of type Boolean
// ERROR: Expected condition of type Boolean
// SKIP_ERRORS_AFTER
// K2_ERROR: EXPECTED_CONDITION
// K2_ERROR: EXPECTED_CONDITION

fun test(n: Int): String {
    return <caret>when {
        is String -> "String"
        in 1..10 -> "1..10"
        else -> "unknown"
    }
}