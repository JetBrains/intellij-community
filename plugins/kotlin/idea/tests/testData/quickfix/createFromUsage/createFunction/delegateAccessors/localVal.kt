// "Create extension function 'String.getValue'" "true"
fun x(parameters: String) {
    val n by <caret>parameters
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix