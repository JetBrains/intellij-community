// "Create Label 'foo'@" "false"
// ERROR: The label '@foo' does not denote a loop
// ERROR: Unresolved reference: @foo
// K2_AFTER_ERROR: NOT_A_LOOP_LABEL
// K2_ERROR: NOT_A_LOOP_LABEL

fun bar(f: () -> Unit) { }

fun test() {
    while (true) {
        bar { break@<caret>foo }
    }
}