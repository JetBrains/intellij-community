// FIX: Replace 'if' expression with safe access expression
// HIGHLIGHT: INFORMATION

fun test(foo: Any) {
    i<caret>f (foo is Int) foo + foo else null
}