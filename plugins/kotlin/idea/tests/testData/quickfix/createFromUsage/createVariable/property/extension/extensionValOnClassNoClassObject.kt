// "Create extension property 'A.Companion.foo'" "true"
class A<T>(val n: T)

fun test() {
    val a: Int = A.<caret>foo
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreatePropertyFromUsageBuilder$CreatePropertyFromUsageAction