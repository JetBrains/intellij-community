// "Create member function 'A.foo'" "true"
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction
// K2_ERROR: Too many arguments for 'fun foo(a: Int): A<Int>'.

class A<T>(val n: T) {
    fun foo(a: Int): A<T> = throw Exception()
}

fun test() {
    val a: A<Int> = A(1).foo(2, <caret>"2")
}
