// "Create member function 'A.compareTo'" "true"
// K2_ERROR: Unresolved reference 'compareTo' on receiver of type 'A<Int>'.

class A<T>(val n: T)

fun test() {
    A(1) <caret>< 2
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction