// "Create property 'foo'" "true"
// ERROR: Property must be initialized or be abstract

class Test {
    val foo1 = 1

    fun test(): Int {
        return <caret>foo
    }

    val foo2 = 42
}

val bar = 1

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreatePropertyFromUsageBuilder$CreatePropertyFromUsageAction