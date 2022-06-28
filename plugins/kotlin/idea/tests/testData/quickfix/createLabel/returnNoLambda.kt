// "Create label foo@" "false"
// ACTION: Do not show return expression hints
// ERROR: Unresolved reference: @foo

fun test(): Int {
    return@<caret>foo 1
}