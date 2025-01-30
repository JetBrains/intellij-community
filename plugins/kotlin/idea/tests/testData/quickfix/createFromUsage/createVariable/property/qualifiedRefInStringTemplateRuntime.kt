// "Create member property 'A.foo'" "true"
// K2_ACTION: "Create property 'foo'" "true"
// ERROR: Property must be initialized or be abstract

class A

fun test() {
    println("a = ${A().<caret>foo}")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreatePropertyFromUsageBuilder$CreatePropertyFromUsageAction