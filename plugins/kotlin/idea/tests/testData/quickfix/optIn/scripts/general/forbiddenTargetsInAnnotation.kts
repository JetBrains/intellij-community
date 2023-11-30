// "Remove forbidden opt-in annotation targets" "true"
// RUNTIME_WITH_SCRIPT_RUNTIME
@RequiresOptIn
@Target(<caret>AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.FILE)
annotation class SomeOptInAnnotation

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveWrongOptInAnnotationTargetFix