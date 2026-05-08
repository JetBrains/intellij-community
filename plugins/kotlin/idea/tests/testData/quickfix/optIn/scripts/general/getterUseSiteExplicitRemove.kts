// "Remove annotation" "true"
// RUNTIME_WITH_SCRIPT_RUNTIME
// K2_ERROR: Opt-in requirement marker annotation cannot be used on getter.

@RequiresOptIn
annotation class SomeOptInAnnotation

@get:SomeOptInAnnotation<caret>
val someProperty: Int = 5

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix