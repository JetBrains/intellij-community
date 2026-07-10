// AFTER_ERROR: Unresolved reference: a
// AFTER_ERROR: Unresolved reference: bar
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
fun foo() {
    <caret>if (a) {
        bar()
        // do something else here
    }
}
