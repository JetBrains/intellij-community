// "Create annotation 'foo'" "false"
// ERROR: Unresolved reference: foo
// IGNORE_K2
// K2_ERROR: UNRESOLVED_REFERENCE
fun test() = <caret>foo(1, "2")