// "Create extension function 'A.foo'" "true"
// WITH_STDLIB
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction
fun bar(b: Boolean) {

}

class A(val n: Int)

fun test() {
    with(A(1)) {
        bar(<caret>foo(n))
    }
}
