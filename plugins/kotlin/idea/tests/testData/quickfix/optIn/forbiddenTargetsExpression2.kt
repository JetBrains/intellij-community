// "Remove forbidden opt-in annotation targets" "true"

@RequiresOptIn
@Target(<caret>AnnotationTarget.EXPRESSION)
annotation class SomeOptInAnnotation
