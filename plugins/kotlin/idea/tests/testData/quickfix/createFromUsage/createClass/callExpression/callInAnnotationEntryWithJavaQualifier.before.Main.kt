// "Create annotation 'bar'" "true"
// ERROR: Unresolved reference: foo
// ERROR: Unresolved reference: bar
/* IGNORE_K2 */
@foo(1, "2", J.<caret>bar("3", 4)) fun test() {

}