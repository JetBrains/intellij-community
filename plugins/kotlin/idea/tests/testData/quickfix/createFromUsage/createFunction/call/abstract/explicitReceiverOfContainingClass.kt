// "Create abstract function 'A.foo'" "true"
abstract class A {
    fun bar(b: Boolean) {}

    fun test(a: A) {
        bar(a.<caret>foo(1, "2"))
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix