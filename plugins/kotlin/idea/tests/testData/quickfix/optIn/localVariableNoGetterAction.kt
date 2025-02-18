// "Move '@SomeOptInAnnotation' annotation from getter to property" "false"
// ACTION: Remove annotation
// ERROR: Opt-in requirement marker annotation cannot be used on variable
// K2_AFTER_ERROR: Opt-in requirement marker annotation cannot be used on variable.

@RequiresOptIn
annotation class SomeOptInAnnotation

fun foo() {
    <caret>@SomeOptInAnnotation
    var x: Int
}
