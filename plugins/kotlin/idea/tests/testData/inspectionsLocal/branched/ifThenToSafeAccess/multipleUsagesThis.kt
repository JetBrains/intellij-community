// FIX: Replace 'if' expression with safe access expression
// HIGHLIGHT: INFORMATION

fun Any.test() {
    i<caret>f (this is Int) this + this else null
}