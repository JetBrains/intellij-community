// "Remove @ from annotation argument" "true"

annotation class Y()
annotation class X(val value: Y)

@X(@Y()<caret>)
fun foo() {
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveAtFromAnnotationArgument
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveAtFromAnnotationArgument