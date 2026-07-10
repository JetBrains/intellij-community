// "class org.jetbrains.kotlin.idea.quickfix.InsertDelegationCallQuickfix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InsertDelegationCallFixFactory$InsertDelegationCallFix" "false"
// ACTION: Insert 'this()' call
// ERROR: Primary constructor call expected
// K2_AFTER_ERROR: PRIMARY_CONSTRUCTOR_DELEGATION_CALL_EXPECTED
// K2_ERROR: PRIMARY_CONSTRUCTOR_DELEGATION_CALL_EXPECTED

open class B()

class A(val x: Int) : B() {
    constructor(x: String)<caret>
}
