// "Create function 'foo'" "true"

class A<T>(val t: T) {
    var x: A<Int> by <caret>foo(t, "")
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix