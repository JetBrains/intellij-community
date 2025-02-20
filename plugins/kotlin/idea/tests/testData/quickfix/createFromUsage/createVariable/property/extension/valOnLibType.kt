// "Create extension property 'Int.foo'" "true"
// WITH_STDLIB
// K2_AFTER_ERROR: Extension property must have accessors or be abstract.

class A<T>(val n: T)

fun test() {
    val a: A<Int> = 2.<caret>foo
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreatePropertyFromUsageBuilder$CreatePropertyFromUsageAction