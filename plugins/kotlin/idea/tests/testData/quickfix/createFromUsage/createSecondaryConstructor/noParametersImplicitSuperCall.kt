// "Create secondary constructor" "true"
// K2_ACTION: "Add primary constructor to 'Creation'" "true"
// K2_ERROR: No value passed for parameter 'f'.
open class Base()

class Creation {
    constructor(f: Int)
}
val v = Creation(<caret>)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.AddConstructorFix