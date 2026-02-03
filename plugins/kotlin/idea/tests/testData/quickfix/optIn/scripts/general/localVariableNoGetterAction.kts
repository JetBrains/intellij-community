// "Move '@SomeOptInAnnotation' annotation from getter to property" "false"
// RUNTIME_WITH_SCRIPT_RUNTIME
// ACTION: Remove annotation
// ERROR: Opt-in requirement marker annotation cannot be used on variable

@RequiresOptIn
annotation class SomeOptInAnnotation

fun foo() {
    <caret>@SomeOptInAnnotation
    var x: Int
}
