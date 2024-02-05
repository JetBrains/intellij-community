// "Create function 'foo'" "true"
fun bar() {
    if (<caret>foo()) return
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix