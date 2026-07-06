// "Create Label 'foo'@" "false"
// ERROR: Unresolved reference: @foo
// K2_AFTER_ERROR: UNRESOLVED_LABEL
// K2_ERROR: UNRESOLVED_LABEL

fun test(): Int {
    return@<caret>foo 1
}