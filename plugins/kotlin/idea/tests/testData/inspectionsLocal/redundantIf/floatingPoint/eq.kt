// HIGHLIGHT: GENERIC_ERROR_OR_WARNING
// FIX: Remove redundant 'if' statement
fun test(): Boolean {
    <caret>if (Double.NaN == Double.NaN) return false
    return true
}