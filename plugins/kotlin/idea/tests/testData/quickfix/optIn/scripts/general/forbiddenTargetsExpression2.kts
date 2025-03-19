// "Remove forbidden opt-in annotation targets" "true"
// RUNTIME_WITH_SCRIPT_RUNTIME
@RequiresOptIn
@Target(<caret>AnnotationTarget.EXPRESSION)
annotation class SomeOptInAnnotation

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveWrongOptInAnnotationTargetFix