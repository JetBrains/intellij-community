// "Create property 'foo'" "true"
// ERROR: Property must be initialized
// K2_AFTER_ERROR: None of the following candidates is applicable:<br><br>constructor(pi: Int): CtorChain:<br>  Argument type mismatch: actual type is 'Any', but 'Int' was expected.<br><br>constructor(ps: String): CtorChain:<br>  Argument type mismatch: actual type is 'Any', but 'String' was expected.<br><br>
// K2_AFTER_ERROR: Property must be initialized.


class CtorChain(val pi: Int) {
    constructor() : this(0)
    constructor(ps: String) : this(f<caret>oo)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreatePropertyFromUsageBuilder$CreatePropertyFromUsageAction

// IGNORE_K1