// FIX: Replace 'if' expression with safe access expression
// HIGHLIGHT: INFORMATION
// IGNORE_K1
fun test(foo: Any) {
    i<caret>f (foo is Int) foo + foo else null
}