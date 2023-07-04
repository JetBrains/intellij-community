// "Remove forbidden opt-in annotation targets" "true"
// RUNTIME_WITH_SCRIPT_RUNTIME
@RequiresOptIn
@Target(<caret>AnnotationTarget.EXPRESSION)
annotation class SomeOptInAnnotation
