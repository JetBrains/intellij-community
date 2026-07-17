// "Move '@SomeOptInAnnotation' annotation from getter to property" "false"
// ACTION: Convert requirement to @OptIn
// ACTION: Remove annotation
// ERROR: Opt-in requirement marker annotation cannot be used on variable
// K2_ERROR: OPT_IN_MARKER_ON_WRONG_TARGET
// K2_AFTER_ERROR: OPT_IN_MARKER_ON_WRONG_TARGET

@RequiresOptIn
annotation class SomeOptInAnnotation

fun foo() {
    <caret>@SomeOptInAnnotation
    var x: Int
}
