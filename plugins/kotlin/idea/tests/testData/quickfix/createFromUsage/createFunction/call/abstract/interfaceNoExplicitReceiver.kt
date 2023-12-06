// "Create abstract function 'foo'" "true"
interface A {
    fun bar(b: Boolean) {}

    fun test() {
        bar(<caret>foo(1, "2"))
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix