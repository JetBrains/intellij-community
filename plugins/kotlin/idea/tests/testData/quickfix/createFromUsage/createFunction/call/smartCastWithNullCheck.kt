// "Create function 'foo'" "true"

fun test(s: String?) {
    if (s == null) return
    <caret>foo(s)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix