// "Insert 'this()' call" "true"
// ERROR: There's a cycle in the delegation calls chain

open class B(val x: Int)

class A : B {
    constructor()<caret>

    constructor(x: String) : super(1)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InsertDelegationCallQuickfix