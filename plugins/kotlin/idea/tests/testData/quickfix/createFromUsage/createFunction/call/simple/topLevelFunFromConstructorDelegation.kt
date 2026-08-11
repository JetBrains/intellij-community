// "Create function 'foo'" "true"
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction
// K2_AFTER_ERROR: NONE_APPLICABLE
// K2_ERROR: UNRESOLVED_REFERENCE
class CtorChain(val pi: Int) {
    constructor() : this(0)
    constructor(ps: String) : this(f<caret>oo())
}
