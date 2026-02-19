// "Create extension functions 'String.getValue', 'String.setValue'" "true"
fun x(parameters: String) {
    var n by <caret>parameters
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix