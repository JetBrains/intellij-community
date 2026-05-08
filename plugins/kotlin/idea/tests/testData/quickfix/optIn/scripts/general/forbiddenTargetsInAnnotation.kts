// "Remove forbidden opt-in annotation targets" "true"
// RUNTIME_WITH_SCRIPT_RUNTIME
// K2_ERROR: Opt-in requirement marker annotation cannot be used on the following code elements: type usage, file.
@RequiresOptIn
@Target(<caret>AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.FILE)
annotation class SomeOptInAnnotation

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveWrongOptInAnnotationTargetFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveWrongOptInAnnotationTargetFix