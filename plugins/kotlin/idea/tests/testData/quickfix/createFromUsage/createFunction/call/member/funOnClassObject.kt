// "Create member function 'A.Companion.foo'" "true"
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction

class A<T>(val n: T) {
    companion object {

    }
}

fun test() {
    val a: Int = A.<caret>foo(2)
}
