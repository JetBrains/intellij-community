// HIGHLIGHT: INFORMATION
// FIX: Remove redundant 'if' statement (may change semantics with floating-point types)
fun test(): Boolean {
    <caret>if (Double.NaN > 42) return false
    return true
}
