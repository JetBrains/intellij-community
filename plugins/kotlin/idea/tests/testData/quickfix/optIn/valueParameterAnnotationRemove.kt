// "Remove annotation" "true"
// WITH_STDLIB

@RequiresOptIn
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class SomeOptInAnnotation

class Foo(<caret>@SomeOptInAnnotation val value: Int) {
}
