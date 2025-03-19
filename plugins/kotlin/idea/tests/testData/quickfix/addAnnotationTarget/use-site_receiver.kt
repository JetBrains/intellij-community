// "Add annotation target" "true"
class Foo

@Target
annotation class ReceiverAnn

fun <caret>@receiver:ReceiverAnn Foo.test() {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddAnnotationTargetFix