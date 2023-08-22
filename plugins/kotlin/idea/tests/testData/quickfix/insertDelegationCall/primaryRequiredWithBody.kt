// "Insert 'this()' call" "true"

class A() {
    constructor(x: String)<caret> {

    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InsertDelegationCallQuickfix