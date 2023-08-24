// "Create member function 'A.unaryMinus'" "true"

class A<T>(val n: T)

fun <U> test(u: U) {
    val a: A<U> = <caret>-A(u)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix