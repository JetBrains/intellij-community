// "Create extension function 'String?.notExistingFun'" "true"
fun context(p: String?) {
    p.<caret>notExistingFun()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix