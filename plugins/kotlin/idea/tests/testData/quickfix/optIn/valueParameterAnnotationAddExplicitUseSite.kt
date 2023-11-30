// "Move 'SomeOptInAnnotation' opt-in requirement from value parameter to property" "true"

@RequiresOptIn
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
annotation class SomeOptInAnnotation

class Foo(@SomeOptInAnnotation<caret> val value: Int) {
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MoveOptInRequirementToPropertyFix
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MoveOptInRequirementToPropertyFix