// "Create property 'foo'" "true"
// ERROR: Property must be initialized
// K2_AFTER_ERROR: Property must be initialized.

fun m() {
    f<caret>oo = 42
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreatePropertyFromUsageBuilder$CreatePropertyFromUsageAction