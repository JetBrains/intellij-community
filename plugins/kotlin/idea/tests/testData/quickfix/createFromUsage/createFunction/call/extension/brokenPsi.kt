// "Create extension function 'X.Companion.callSomethingNew'" "true"

class X {
    fun callee() {
        X.<caret>callSomethingNew(123)
    }
    fun test(x:Int): Unit {
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix