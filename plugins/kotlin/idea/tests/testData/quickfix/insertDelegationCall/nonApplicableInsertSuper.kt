// "Insert 'super()' call" "true"
// ERROR: No value passed for parameter 'x'
// K2_AFTER_ERROR: NO_VALUE_FOR_PARAMETER
// K2_ERROR: EXPLICIT_DELEGATION_CALL_REQUIRED

open class B(val x: Int)

class A : B {
    constructor(x: String)<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InsertDelegationCallQuickfix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InsertDelegationCallFixFactory$InsertDelegationCallFix