// "Create property 'foo'" "false"
// WITH_STDLIB
// ERROR: Unresolved reference: foo

class A<T>(val n: T)

fun test() {
    2.<caret>foo = A("2")
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreatePropertyFromUsageBuilder$CreatePropertyFromUsageAction