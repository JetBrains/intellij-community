// ERROR: Unresolved reference: XXX
// ERROR: Unresolved reference: bar
// K2-ERROR: Unresolved reference 'XXX'.
// K2-ERROR: Unresolved reference 'bar'.
// K2-AFTER-ERROR: Unresolved reference 'XXX'.
// K2-AFTER-ERROR: Unresolved reference 'bar'.
fun <caret>foo(): XXX = bar()