// "Insert 'this()' call" "true"
// K2_ERROR: EXPLICIT_DELEGATION_CALL_REQUIRED

open class B(val x: Int)

class A : B {
    constructor(x: String)<caret>

    constructor() : super(1)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InsertDelegationCallQuickfix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InsertDelegationCallFixFactory$InsertDelegationCallFix