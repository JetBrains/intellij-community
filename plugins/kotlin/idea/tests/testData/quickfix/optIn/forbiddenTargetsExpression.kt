// "Remove forbidden opt-in annotation targets" "true"

@RequiresOptIn
@Target(<caret>AnnotationTarget.CLASS, AnnotationTarget.EXPRESSION, AnnotationTarget.FUNCTION)
annotation class SomeOptInAnnotation
