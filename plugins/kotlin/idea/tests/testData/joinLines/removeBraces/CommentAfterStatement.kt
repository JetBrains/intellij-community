// AFTER_ERROR: Unresolved reference: a
// AFTER_ERROR: Unresolved reference: bar
// K2_AFTER_ERROR: Unresolved reference 'a'.
// K2_AFTER_ERROR: Unresolved reference 'bar'.
fun foo() {
    <caret>if (a) {
        bar() // do bar
    }
}
