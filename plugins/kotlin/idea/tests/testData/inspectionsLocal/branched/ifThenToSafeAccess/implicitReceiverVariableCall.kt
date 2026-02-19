// HIGHLIGHT: INFORMATION
// FIX: Replace 'if' expression with safe access expression
// IGNORE_K1
class A(val f: () -> Unit)

fun Any.foo() = <caret>if (this !is A) null else f()
