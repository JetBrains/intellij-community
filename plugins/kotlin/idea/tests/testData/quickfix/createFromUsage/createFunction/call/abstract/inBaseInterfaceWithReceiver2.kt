// "Create abstract function 'I.bar'" "true"
// ERROR: Class 'Foo' is not abstract and does not implement abstract member public abstract fun bar(): Unit defined in I

interface I

class Foo : I

fun test(foo: Foo) {
    foo.<caret>bar()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction