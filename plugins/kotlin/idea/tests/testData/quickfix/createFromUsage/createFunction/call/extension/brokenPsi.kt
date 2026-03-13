// "Create extension function 'X.Companion.callSomethingNew'" "true"
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction
// K2_ERROR: Unresolved reference 'callSomethingNew'.

class X {
    fun callee() {
        X.<caret>callSomethingNew(123)
    }
    fun test(x:Int): Unit {
