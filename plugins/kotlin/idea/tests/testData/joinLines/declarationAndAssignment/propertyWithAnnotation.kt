// AFTER_ERROR: This annotation is not applicable to target 'local variable'
// K2_AFTER_ERROR: This annotation is not applicable to target 'local variable'. Applicable targets: field
fun foo() {
    <caret>@Volatile var v: Int
    v = 1
}