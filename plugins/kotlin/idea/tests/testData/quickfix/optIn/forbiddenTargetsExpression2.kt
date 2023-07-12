// "Remove forbidden opt-in annotation targets" "true"
// IGNORE_FIR
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_STDLIB
@RequiresOptIn
@Target(<caret>AnnotationTarget.EXPRESSION)
annotation class SomeOptInAnnotation
