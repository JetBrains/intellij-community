// "Create member function 'A.foo'" "true"

class A<T>(val n: T)

fun <U> test(u: U) {
    val a: A<U> = A(u).<caret>foo(u)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix