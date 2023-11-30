// "Create function 'g'" "true"
class C {
    inline fun f() {
        <caret>g()
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix