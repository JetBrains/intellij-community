// "Insert 'this()' call" "true"
// K2_ERROR: PRIMARY_CONSTRUCTOR_DELEGATION_CALL_EXPECTED

class A() {
    constructor(x: String)<caret> {

    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InsertDelegationCallFixFactory$InsertDelegationCallFix