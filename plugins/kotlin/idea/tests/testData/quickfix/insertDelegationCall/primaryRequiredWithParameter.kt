// "Insert 'this()' call" "true"
// ERROR: None of the following functions can be called with the arguments supplied: <br>public constructor A(x: Int) defined in A<br>public constructor A(x: String) defined in A
// K2_AFTER_ERROR: None of the following candidates is applicable:<br><br>constructor(x: Int): A:<br>  No value passed for parameter 'x'.<br><br>constructor(x: String): A:<br>  No value passed for parameter 'x'.<br><br>

class A(val x: Int) {
    constructor(x: String)<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InsertDelegationCallQuickfix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InsertDelegationCallFixFactory$InsertDelegationCallFix