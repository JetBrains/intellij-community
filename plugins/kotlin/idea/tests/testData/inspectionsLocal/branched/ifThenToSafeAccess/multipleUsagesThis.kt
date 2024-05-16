// FIX: Replace 'if' expression with safe access expression
// HIGHLIGHT: INFORMATION
// IGNORE_K1
fun Any.test() {
    i<caret>f (this is Int) this + this else null
}