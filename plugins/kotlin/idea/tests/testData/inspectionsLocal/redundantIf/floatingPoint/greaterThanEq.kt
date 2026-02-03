// HIGHLIGHT: INFORMATION
// FIX: Remove redundant 'if' statement (may change semantics with floating-point types)
fun test(f: Float): Boolean {
    <caret>if (f >= 42) return false
    return true
}