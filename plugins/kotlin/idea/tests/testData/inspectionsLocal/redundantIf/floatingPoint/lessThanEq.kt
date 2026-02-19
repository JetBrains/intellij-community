// HIGHLIGHT: INFORMATION
// FIX: Remove redundant 'if' statement (may change semantics with floating-point types)
fun test(): Boolean {
    <caret>if (42 <= Float.NaN) return false
    return true
}