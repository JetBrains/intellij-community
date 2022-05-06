// "Create label foo@" "false"
// ACTION: Do not show return expression hints
// ERROR: The label '@foo' does not denote a loop
// ERROR: Unresolved reference: @foo

fun bar(f: () -> Unit) { }

fun test() {
    while (true) {
        bar { break@<caret>foo }
    }
}