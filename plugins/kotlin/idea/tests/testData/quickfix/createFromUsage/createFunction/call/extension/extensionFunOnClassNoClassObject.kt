// "Create extension function 'A.Companion.foo'" "true"
// KEEP_ACTIONS_LIST_ORDER
// K2_ACTIONS_LIST: Create extension function 'A.Companion.foo'
// K2_ACTIONS_LIST: Create member function 'A.Companion.foo'
// K2_ACTIONS_LIST: Create annotation 'foo'
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction

class A<T>(val n: T)

fun test() {
    val a: Int = A.<caret>foo(2)
}
