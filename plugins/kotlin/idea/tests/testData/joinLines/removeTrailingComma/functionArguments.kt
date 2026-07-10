// AFTER_ERROR: Unresolved reference: awdawd
// AFTER_ERROR: Unresolved reference: b
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
fun a() {
    b(1, 3,<caret> 2424,
    awdawd,)
}