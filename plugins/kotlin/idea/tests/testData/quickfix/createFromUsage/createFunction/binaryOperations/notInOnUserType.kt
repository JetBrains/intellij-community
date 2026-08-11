// "Create member function 'A.contains'" "true"
// K2_ERROR: NONE_APPLICABLE
// K2_ERROR: UNRESOLVED_REFERENCE

class A<T>(val n: T)

fun test() {
    2 <caret>!in A(1)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction