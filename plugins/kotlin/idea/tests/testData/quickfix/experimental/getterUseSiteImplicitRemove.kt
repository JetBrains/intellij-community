// "Remove annotation" "true"
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
// WITH_RUNTIME

@RequiresOptIn
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER)
annotation class SomeOptInAnnotation

class Foo(val value: Int) {
    val bar: Boolean
        <caret>@SomeOptInAnnotation get() = value > 0
}
