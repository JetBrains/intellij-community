// "Create secondary constructor" "true"
// K2_ACTION: "Add primary constructor to 'A'" "true"
// K2_ERROR: NONE_APPLICABLE
// K2_ERROR: NO_VALUE_FOR_PARAMETER

open class A {
    constructor(x: Int, y: Int)
    constructor(x: Int, y: String)
}

class B : <caret>A(1)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.AddConstructorFix