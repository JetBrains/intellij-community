// "Insert 'this()' call" "true"
// ERROR: There's a cycle in the delegation calls chain
// K2_ERROR: Explicit 'this' or 'super' call is required. There is no constructor in the superclass that can be called without arguments.
// K2_AFTER_ERROR: There's a cycle in the delegation calls chain.

open class B(val x: Int)

class A : B {
    constructor(number: Int = 42)<caret>

    constructor(x: String) : super(1)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InsertDelegationCallQuickfix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InsertDelegationCallFixFactory$InsertDelegationCallFix