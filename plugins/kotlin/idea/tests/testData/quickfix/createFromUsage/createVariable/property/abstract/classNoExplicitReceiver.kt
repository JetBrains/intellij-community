// "Create abstract property 'foo'" "true"
abstract class A {
    fun bar(b: Boolean) {}

    fun test() {
        bar(<caret>foo)
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix