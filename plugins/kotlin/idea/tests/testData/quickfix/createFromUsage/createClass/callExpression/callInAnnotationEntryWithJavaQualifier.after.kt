// "Create annotation 'bar'" "true"
// ERROR: Unresolved reference: foo
// ERROR: Unresolved reference: bar

@foo(1, "2", J.bar("3", 4)) fun test() {

}