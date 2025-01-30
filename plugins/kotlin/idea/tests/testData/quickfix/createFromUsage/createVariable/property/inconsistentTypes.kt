// "Create member property 'A.foo'" "true"
// K2_ACTION: "Create property 'foo'" "true"
// ERROR: Type mismatch: inferred type is A<Int> but Int was expected
// ERROR: Property must be initialized or be abstract

class A<T>(val n: T)

fun test(): Int {
    return A(1).<caret>foo as A<Int>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreatePropertyFromUsageBuilder$CreatePropertyFromUsageAction