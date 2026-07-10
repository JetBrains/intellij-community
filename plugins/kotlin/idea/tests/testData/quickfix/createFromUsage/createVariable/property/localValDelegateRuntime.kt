// "Create property 'foo'" "true"
// ERROR: Property must be initialized
// K2_AFTER_ERROR: MUST_BE_INITIALIZED
// K2_ERROR: UNRESOLVED_REFERENCE

fun test() {
    val x: Int by <caret>foo
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreatePropertyFromUsageBuilder$CreatePropertyFromUsageAction