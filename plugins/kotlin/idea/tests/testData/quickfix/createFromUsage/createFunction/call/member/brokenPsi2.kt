// "Create member function 'X.Companion.callSomethingNew'" "true"
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction
// K2_ERROR: SYNTAX
// K2_ERROR: UNRESOLVED_REFERENCE
// K2_AFTER_ERROR: SYNTAX

class X {
    fun callee() {
        X.<caret>callSomethingNew(123)
    }
    fun test(x:Int): Unit {
