// "Create enum constant 'A'" "false"
// K2_ACTION: "Create enum constant 'A'" "true"
// ERROR: Unresolved reference: A
internal fun foo(): J = J.<caret>A
