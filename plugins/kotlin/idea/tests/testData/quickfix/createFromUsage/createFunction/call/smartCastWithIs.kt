// "Create function 'foo'" "true"

fun test(o: Any) {
    if (o is String) <caret>foo(o)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix