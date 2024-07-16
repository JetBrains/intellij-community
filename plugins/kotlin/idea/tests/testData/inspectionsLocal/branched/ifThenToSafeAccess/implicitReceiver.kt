// HIGHLIGHT: WARNING
// FIX: Replace 'if' expression with safe access expression
// WITH_STDLIB
fun String?.foo() = <caret>if (this == null) null else isEmpty()
