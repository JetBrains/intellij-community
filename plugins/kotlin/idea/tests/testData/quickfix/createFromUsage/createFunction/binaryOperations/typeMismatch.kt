// "Create member function 'A.times'" "true"
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
class A

operator fun A.times(i: Int) = this

fun test() {
    A() * <caret>"1"
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction