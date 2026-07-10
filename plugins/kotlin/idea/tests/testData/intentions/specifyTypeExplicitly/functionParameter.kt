// IS_APPLICABLE: false
// ERROR: A type annotation is required on a value parameter
// K2_ERROR: VALUE_PARAMETER_WITHOUT_EXPLICIT_TYPE
fun test(
    x<caret> = 1
) {
}