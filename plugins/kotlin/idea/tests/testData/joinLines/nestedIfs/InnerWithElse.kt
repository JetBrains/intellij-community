// AFTER_ERROR: Unresolved reference: a
// AFTER_ERROR: Unresolved reference: b
// AFTER_ERROR: Unresolved reference: bar
// K2_AFTER_ERROR: Unresolved reference 'a'.
// K2_AFTER_ERROR: Unresolved reference 'b'.
// K2_AFTER_ERROR: Unresolved reference 'bar'.
fun foo() {
    <caret>if (a) {
        if (b) foo() else bar()
    }
}
