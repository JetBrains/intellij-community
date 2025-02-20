// "Create label foo@" "false"
// ERROR: The label '@foo' does not denote a loop
// ERROR: Unresolved reference: @foo
// K2_AFTER_ERROR: 'break' and 'continue' are only allowed inside loops.

fun test() {
    break@<caret>foo
}