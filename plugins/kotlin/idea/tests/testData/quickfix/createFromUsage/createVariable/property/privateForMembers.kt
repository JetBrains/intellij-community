// "Create property 'foo'" "true"
// ERROR: Property must be initialized or be abstract
class A {
    fun test() {
        <caret>foo = 1
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix