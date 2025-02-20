// "Create label foo@" "false"
// ERROR: The label '@foo' does not denote a loop
// ERROR: Unresolved reference: @foo
// K2_AFTER_ERROR: Label does not denote a reachable loop.

fun bar(f: () -> Unit) { }

fun test() {
    while (true) {
        bar { break@<caret>foo }
    }
}