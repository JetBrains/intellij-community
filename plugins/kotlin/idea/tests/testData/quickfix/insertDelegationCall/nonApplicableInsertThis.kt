// "Insert 'this()' call" "true"
// K2_ERROR: Explicit 'this' or 'super' call is required. There is no constructor in the superclass that can be called without arguments.

open class B(val x: Int)

class A : B {
    constructor(x: String)<caret>

    constructor() : super(1)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InsertDelegationCallQuickfix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InsertDelegationCallFixFactory$InsertDelegationCallFix