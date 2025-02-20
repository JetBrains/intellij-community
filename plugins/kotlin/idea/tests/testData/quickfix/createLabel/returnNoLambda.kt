// "Create label foo@" "false"
// ERROR: Unresolved reference: @foo
// K2_AFTER_ERROR: Unresolved label.

fun test(): Int {
    return@<caret>foo 1
}