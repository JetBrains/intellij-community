// "Create label foo@" "false"
// ACTION: Do not show return expression hints
// ERROR: The label '@foo' does not denote a loop
// ERROR: Unresolved reference: @foo

fun test() {
    break@<caret>foo
}