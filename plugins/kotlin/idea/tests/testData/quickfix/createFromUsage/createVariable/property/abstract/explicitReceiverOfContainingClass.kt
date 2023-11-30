// "Create abstract property 'A.foo'" "true"
abstract class A {
    fun bar(b: Boolean) {}

    fun test(a: A) {
        bar(a.<caret>foo)
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix