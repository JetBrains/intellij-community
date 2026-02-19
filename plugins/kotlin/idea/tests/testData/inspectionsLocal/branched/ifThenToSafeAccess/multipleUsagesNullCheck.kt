// FIX: Replace 'if' expression with safe access expression
// HIGHLIGHT: INFORMATION
// IGNORE_K1
fun test(baz: Int?) {
    i<caret>f (baz != null) baz + baz else null
}