// "Create property 'foo'" "true"
// ERROR: Property must be initialized
// K2_AFTER_ERROR: Const 'val' must have an initializer.
// K2_AFTER_ERROR: Property must be initialized.

annotation class AnnInt(val value: Int)

@AnnInt(fo<caret>o)
class AnnotatedWithInt

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreatePropertyFromUsageBuilder$CreatePropertyFromUsageAction
// IGNORE_K1