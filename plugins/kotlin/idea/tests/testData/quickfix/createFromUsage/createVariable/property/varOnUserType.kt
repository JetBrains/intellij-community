// "Create member property 'A.foo'" "true"
// K2_ACTION: "Create property 'foo'" "true"

class A<T>(val n: T)

fun test() {
    A(1).<caret>foo = "1"
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreatePropertyFromUsageBuilder$CreatePropertyFromUsageAction