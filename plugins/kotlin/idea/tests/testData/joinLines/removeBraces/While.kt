// AFTER_ERROR: Unresolved reference: bar
// K2_AFTER_ERROR: Unresolved reference 'bar'.
fun foo() {
    <caret>while (true) {
        if (bar()) break
    }
}
