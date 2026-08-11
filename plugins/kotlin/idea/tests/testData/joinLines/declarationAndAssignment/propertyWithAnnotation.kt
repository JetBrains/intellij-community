// AFTER_ERROR: This annotation is not applicable to target 'local variable'
// K2_AFTER_ERROR: WRONG_ANNOTATION_TARGET
fun foo() {
    <caret>@Volatile var v: Int
    v = 1
}