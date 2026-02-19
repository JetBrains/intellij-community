// "Move annotation to receiver type" "true"

annotation class Ann

@receiver:Ann<caret>
val String.bar get() = ""
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MoveReceiverAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MoveReceiverAnnotationFix