// "Create function 'foo'" "true"
// IGNORE_K2

class A<T>(val t: T) {
    val x: A<Int> by <caret>foo(t, "")
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix