// "Remove annotation" "true"
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_STDLIB

@RequiresOptIn
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class SomeOptInAnnotation

class Foo(<caret>@SomeOptInAnnotation val value: Int) {
}
