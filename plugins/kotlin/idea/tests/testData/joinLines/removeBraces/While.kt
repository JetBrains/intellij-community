// AFTER_ERROR: Unresolved reference: bar
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
fun foo() {
    <caret>while (true) {
        if (bar()) break
    }
}
