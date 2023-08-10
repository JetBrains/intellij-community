// "Create function 'foo'" "true"
fun test(p: Boolean) {
    if (!foo<caret>()) {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix