// "Create function 'foo'" "true"
class A {
    fun test() {
        <caret>foo()
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix