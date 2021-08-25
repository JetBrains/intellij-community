// "Replace annotation with '@property:SomeOptInAnnotation'" "true"
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
// WITH_RUNTIME

@RequiresOptIn
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
annotation class SomeOptInAnnotation

class Foo(@SomeOptInAnnotation<caret> val value: Int) {
}
