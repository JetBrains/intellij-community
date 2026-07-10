// "Create property 'foo'" "true"
// ERROR: Property must be initialized
// K2_AFTER_ERROR: MUST_BE_INITIALIZED
// K2_AFTER_ERROR: NONE_APPLICABLE
// K2_ERROR: UNRESOLVED_REFERENCE


class CtorChain(val pi: Int) {
    constructor() : this(0)
    constructor(ps: String) : this(f<caret>oo)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreatePropertyFromUsageBuilder$CreatePropertyFromUsageAction

