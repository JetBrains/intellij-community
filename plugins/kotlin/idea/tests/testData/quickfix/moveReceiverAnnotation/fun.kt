// "Move annotation to receiver type" "true"
// K2_ERROR: WRONG_ANNOTATION_TARGET_WITH_USE_SITE_TARGET

annotation class Ann

@receiver:Ann<caret>
fun String.foo() {
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MoveReceiverAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MoveReceiverAnnotationFix