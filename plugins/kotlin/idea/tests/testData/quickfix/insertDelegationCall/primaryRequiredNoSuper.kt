// "class org.jetbrains.kotlin.idea.quickfix.InsertDelegationCallQuickfix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InsertDelegationCallFixFactory$InsertDelegationCallFix" "false"
// ACTION: Insert 'this()' call
// ERROR: Primary constructor call expected
// K2_AFTER_ERROR: Primary constructor call expected.

open class B()

class A(val x: Int) : B() {
    constructor(x: String)<caret>
}
