// IS_APPLICABLE: false
// ERROR: A type annotation is required on a value parameter
// K2_ERROR: An explicit type is required on a value parameter.
fun test(
    x<caret> = 1
) {
}