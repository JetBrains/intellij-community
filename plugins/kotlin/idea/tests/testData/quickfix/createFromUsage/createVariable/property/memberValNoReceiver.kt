// "Create property 'foo'" "true"
// ERROR: Property must be initialized or be abstract

class A {
    class B {
        fun test(): Int {
            return <caret>foo
        }
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix