// AFTER_ERROR: Unresolved reference: b
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
fun a() {
    b<<caret>Int,
    >()
}