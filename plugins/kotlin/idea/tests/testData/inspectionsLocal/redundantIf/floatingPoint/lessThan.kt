// HIGHLIGHT: INFORMATION
// FIX: Remove redundant 'if' statement (may change semantics with floating-point types)
fun test(d: Double): Boolean {
    <caret>if (42 < d) return false
    return true
}