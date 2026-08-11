// PRIORITY: LOW
// K2_AFTER_ERROR: SYNTAX
fun test(n: Int): String {
    return <caret>when (n) {
        is -> "String"
        in -> "1..10"
        else -> "unknown"
    }
}