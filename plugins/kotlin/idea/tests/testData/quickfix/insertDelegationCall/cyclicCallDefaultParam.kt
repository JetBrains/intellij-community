// "Insert 'this()' call" "true"
// ERROR: There's a cycle in the delegation calls chain
// K2_AFTER_ERROR: CYCLIC_CONSTRUCTOR_DELEGATION_CALL
// K2_ERROR: EXPLICIT_DELEGATION_CALL_REQUIRED

open class B(val x: Int)

class A : B {
    constructor(number: Int = 42)<caret>

    constructor(x: String) : super(1)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InsertDelegationCallQuickfix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InsertDelegationCallFixFactory$InsertDelegationCallFix