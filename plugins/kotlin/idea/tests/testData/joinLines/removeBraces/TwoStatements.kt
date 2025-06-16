// AFTER_ERROR: Unresolved reference: a
// AFTER_ERROR: Unresolved reference: bar1
// AFTER_ERROR: Unresolved reference: bar2
// K2_AFTER_ERROR: Unresolved reference 'a'.
// K2_AFTER_ERROR: Unresolved reference 'bar1'.
// K2_AFTER_ERROR: Unresolved reference 'bar2'.
fun foo() {
    <caret>if (a) {
        bar1(); bar2()
    }
}
