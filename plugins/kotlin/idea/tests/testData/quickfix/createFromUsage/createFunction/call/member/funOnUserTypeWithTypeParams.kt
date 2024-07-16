// "Create member function 'A.foo'" "true"
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction
// TODO: getExpectedType in K2 generates type `U` which is inaccessible in class `A`
// IGNORE_K2
class A<T>(val n: T)

fun <U> test(u: U) {
    val a: A<U> = A(u).<caret>foo(u)
}
