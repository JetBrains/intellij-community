// IS_APPLICABLE: false
// ERROR: A type annotation is required on a value parameter
// K2-ERROR: An explicit type is required on a value parameter.
class D(
    val x<caret> = 1
)