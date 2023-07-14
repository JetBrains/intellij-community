// "Create member function 'A.foo'" "true"

class A<T>(val n: T)

fun test() {
    val a: A<Int>? = A(1).<caret>foo(2)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix