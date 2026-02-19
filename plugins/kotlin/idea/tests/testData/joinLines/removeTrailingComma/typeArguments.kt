// AFTER_ERROR: Unresolved reference: b
// K2_AFTER_ERROR: Unresolved reference 'b'.
fun a() {
    b<<caret>Int,
    >()
}