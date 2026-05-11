// "Create member function 'A.plus'" "true"
// K2_ERROR: Unresolved reference 'plus' for operator '+' on receiver of type 'A<Int>'.

class A<T>(val n: T)

fun test() {
    var a = A(1)
    a = a <caret>+ 2
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction