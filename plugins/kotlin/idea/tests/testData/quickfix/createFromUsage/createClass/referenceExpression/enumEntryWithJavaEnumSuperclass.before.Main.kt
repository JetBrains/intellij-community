// "Create enum constant 'A'" "false"
// ACTION: Convert to block body
// ACTION: Create member property 'J.A'
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ERROR: Unresolved reference: A
internal fun foo(): J = J.<caret>A
