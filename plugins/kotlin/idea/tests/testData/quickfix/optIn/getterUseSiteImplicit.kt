// "Move 'SomeOptInAnnotation' opt-in requirement from getter to property" "true"
// K2_ERROR: Opt-in requirement marker annotation cannot be used on getter.

@RequiresOptIn
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER)
annotation class SomeOptInAnnotation

class Foo(val value: Int) {
    val bar: Boolean
        <caret>@SomeOptInAnnotation get() = value > 0
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MoveOptInRequirementToPropertyFix
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MoveOptInRequirementToPropertyFix