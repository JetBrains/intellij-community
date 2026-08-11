// AFTER_ERROR: Unresolved reference: a
// AFTER_ERROR: Unresolved reference: bar1
// AFTER_ERROR: Unresolved reference: bar2
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
fun foo() {
    <caret>if (a) bar1() else {
        bar2()
    }
}
