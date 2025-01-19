// PRIORITY: LOW
// K2-AFTER-ERROR: Syntax error: Incomplete code.
fun test(n: Int): String {
    return <caret>when (n) {
        is -> "String"
        in -> "1..10"
        else -> "unknown"
    }
}