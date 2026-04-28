// "Create member function 'A.plusAssign'" "true"
// K2_ERROR: Unresolved reference 'plusAssign' on receiver of type 'A<Int>'.

class A<T>(val n: T)

fun test() {
    A(1) <caret>+= 2
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction