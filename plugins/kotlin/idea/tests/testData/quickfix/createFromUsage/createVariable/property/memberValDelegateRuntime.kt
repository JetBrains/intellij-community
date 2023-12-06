// "Create property 'foo'" "true"
// ERROR: Property must be initialized or be abstract
// ERROR: Variable 'foo' must be initialized

class A<T> {
    val x: A<Int> by <caret>foo
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix