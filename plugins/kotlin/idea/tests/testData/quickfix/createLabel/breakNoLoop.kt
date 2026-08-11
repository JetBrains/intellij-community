// "Create Label 'foo'@" "false"
// ERROR: The label '@foo' does not denote a loop
// ERROR: Unresolved reference: @foo
// K2_AFTER_ERROR: BREAK_OR_CONTINUE_OUTSIDE_A_LOOP
// K2_ERROR: BREAK_OR_CONTINUE_OUTSIDE_A_LOOP

fun test() {
    break@<caret>foo
}