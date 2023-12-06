// "Create function 'foo'" "true"

class A {
    object B {
        fun test(): Int {
            return <caret>foo(2, "2")
        }
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix