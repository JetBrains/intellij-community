// "Create function 'bar'" "false"
// ERROR: Unresolved reference: foo
// ERROR: Unresolved reference: bar

@foo(1, "2", <caret>bar("3", 4)) fun test() {

}