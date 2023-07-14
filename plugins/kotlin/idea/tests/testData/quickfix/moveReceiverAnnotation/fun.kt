// "Move annotation to receiver type" "true"

annotation class Ann

@receiver:Ann<caret>
fun String.foo() {
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MoveReceiverAnnotationFix