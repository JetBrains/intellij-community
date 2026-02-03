// "Move 'SomeOptInAnnotation' opt-in requirement from getter to property" "true"
// RUNTIME_WITH_SCRIPT_RUNTIME

@RequiresOptIn
annotation class SomeOptInAnnotation

@get:SomeOptInAnnotation<caret>
val someProperty: Int = 5

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MoveOptInRequirementToPropertyFix