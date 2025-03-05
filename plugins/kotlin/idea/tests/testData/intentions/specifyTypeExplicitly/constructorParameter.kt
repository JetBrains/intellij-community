// IS_APPLICABLE: false
// ERROR: A type annotation is required on a value parameter
// K2_ERROR: An explicit type is required on a value parameter.
class D(
    val x<caret> = 1
)