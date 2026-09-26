// "class org.jetbrains.kotlin.idea.quickfix.InsertDelegationCallQuickfix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InsertDelegationCallFixFactory$InsertDelegationCallFix" "false"
// ACTION: Insert 'super()' call
// ACTION: Convert to primary constructor
// ERROR: Explicit 'this' or 'super' call is required. There is no constructor in superclass that can be called without arguments
// K2_AFTER_ERROR: EXPLICIT_DELEGATION_CALL_REQUIRED
// K2_ERROR: EXPLICIT_DELEGATION_CALL_REQUIRED

open class B(val x: Int)

class A : B {
    constructor(x: String)<caret>
}
