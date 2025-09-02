// "Create parameter 'x'" "true"
// K2_AFTER_ERROR: None of the following candidates is applicable:<br>constructor(): CtorAccess<br>constructor(ps: String, x: String): CtorAccess
// ERROR: None of the following functions can be called with the arguments supplied: <br>public constructor CtorAccess() defined in CtorAccess<br>public constructor CtorAccess(ps: String, x: String) defined in CtorAccess

class CtorAccess() {
    constructor(ps: String) : this(<caret>x)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateParameterFromUsageBuilder$CreateParameterFromUsageAction