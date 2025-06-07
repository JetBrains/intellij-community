// AFTER_ERROR: Unresolved reference: awdawd
// AFTER_ERROR: Unresolved reference: b
// K2_AFTER_ERROR: Unresolved reference 'awdawd'.
// K2_AFTER_ERROR: Unresolved reference 'b'.
fun a() {
    b(1, 3,<caret> 2424,
    awdawd,)
}