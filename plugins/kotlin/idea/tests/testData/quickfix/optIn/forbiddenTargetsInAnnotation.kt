// "Remove forbidden opt-in annotation targets" "true"
// K2_ERROR: Opt-in requirement marker annotation cannot be used on the following code elements: type usage, file.

@RequiresOptIn
@Target(<caret>AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.FILE)
annotation class SomeOptInAnnotation

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveWrongOptInAnnotationTargetFix
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveWrongOptInAnnotationTargetFix