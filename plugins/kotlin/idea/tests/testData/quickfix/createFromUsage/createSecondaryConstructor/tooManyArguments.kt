// "Create secondary constructor" "true"
// K2_ACTION: "Add secondary constructor to 'CtorPrimary'" "true"
// K2_ERROR: Too many arguments for 'constructor(f1: Int, f2: Int?): CtorPrimary'.

class CtorPrimary(val f1: Int, val f2: Int?)

fun construct() {
    val v6 = CtorPrimary(1, 2, 3<caret>)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.AddConstructorFix