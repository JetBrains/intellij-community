// "Create property 'foo'" "true"
// ERROR: Property must be initialized
// K2_AFTER_ERROR: CONST_VAL_WITHOUT_INITIALIZER
// K2_AFTER_ERROR: MUST_BE_INITIALIZED
// K2_ERROR: UNRESOLVED_REFERENCE

annotation class AnnInt(val value: Int)

@AnnInt(fo<caret>o)
class AnnotatedWithInt

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreatePropertyFromUsageBuilder$CreatePropertyFromUsageAction
