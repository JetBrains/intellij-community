// "Move 'SomeOptInAnnotation' opt-in requirement from value parameter to property" "true"

@RequiresOptIn
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
annotation class SomeOptInAnnotation

class Foo(@SomeOptInAnnotation<caret> val value: Int) {
}
