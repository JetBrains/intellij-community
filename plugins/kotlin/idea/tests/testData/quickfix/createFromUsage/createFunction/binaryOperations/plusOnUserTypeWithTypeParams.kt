// "Create member function 'A.plus'" "true"

class A<T>(val n: T)

fun <U> test(u: U) {
    val a: A<U> = A(u) <caret>+ 2
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix