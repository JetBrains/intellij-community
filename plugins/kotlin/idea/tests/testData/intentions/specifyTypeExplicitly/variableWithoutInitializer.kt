// IS_APPLICABLE: false
// ERROR: This variable must either have a type annotation or be initialized
// K2_ERROR: This variable must either have an explicit type or be initialized.
fun test() {
    val x<caret>
}
