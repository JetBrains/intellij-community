// "Move 'SomeOptInAnnotation' opt-in requirement from getter to property" "true"
// K2_ERROR: Opt-in requirement marker annotation cannot be used on getter.

@RequiresOptIn
annotation class SomeOptInAnnotation

@get:SomeOptInAnnotation<caret>
val someProperty: Int = 5

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MoveOptInRequirementToPropertyFix
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MoveOptInRequirementToPropertyFix