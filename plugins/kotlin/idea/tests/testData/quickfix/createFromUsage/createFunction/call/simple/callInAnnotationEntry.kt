// "Create function 'bar'" "false"
// ERROR: Unresolved reference: foo
// ERROR: Unresolved reference: bar
// K2_AFTER_ERROR: Unresolved reference 'bar'.
// K2_AFTER_ERROR: Unresolved reference 'foo'.

@foo(1, "2", <caret>bar("3", 4)) fun test() {

}