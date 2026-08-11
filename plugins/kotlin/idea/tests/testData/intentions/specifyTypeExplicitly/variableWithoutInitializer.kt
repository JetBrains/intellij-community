// IS_APPLICABLE: false
// ERROR: This variable must either have a type annotation or be initialized
// K2_ERROR: VARIABLE_WITH_NO_TYPE_NO_INITIALIZER
fun test() {
    val x<caret>
}
