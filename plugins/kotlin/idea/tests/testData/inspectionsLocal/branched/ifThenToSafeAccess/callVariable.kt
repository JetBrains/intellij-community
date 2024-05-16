// FIX: Replace 'if' expression with safe access expression
// HIGHLIGHT: INFORMATION
fun test(foo: (() -> Unit)?) {
    <caret>if (foo != null) foo()
}