// "Create extension function 'T.bar'" "true"
fun <T> foo(t: T) {
    t.<caret>bar()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix