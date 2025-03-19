// FIR_COMPARISON
fun foo(handler: () -> Unit) {}

val v = <caret>
// ELEMENT: foo
// CHAR: '\t'
// TAIL_TEXT: " { handler: () -> Unit } (<root>)"