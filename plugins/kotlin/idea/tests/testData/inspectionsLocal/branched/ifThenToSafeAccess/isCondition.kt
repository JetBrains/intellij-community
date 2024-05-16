// FIX: Replace 'if' expression with safe access expression
// HIGHLIGHT: INFORMATION
fun foo(arg: Any) = <caret>if (arg is String) arg.length else null