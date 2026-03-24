// "Create secondary constructor" "true"
// K2_ACTION: "Add primary constructor to 'A'" "true"
// K2_ERROR: Too many arguments for 'constructor(): A'.

class A {

}

fun test() {
    val a = A(<caret>1)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.AddConstructorFix