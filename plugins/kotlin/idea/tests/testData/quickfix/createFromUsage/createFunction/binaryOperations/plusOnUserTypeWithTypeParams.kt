// "Create member function 'A.plus'" "true"
// K2_ERROR: Unresolved reference 'plus' for operator '+' on receiver of type 'A<U (of fun <U> test)>'.

class A<T>(val n: T)

fun <U> test(u: U) {
    val a: A<U> = A(u) <caret>+ 2
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction