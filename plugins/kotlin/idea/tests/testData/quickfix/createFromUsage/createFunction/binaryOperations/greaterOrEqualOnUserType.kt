// "Create member function 'A.compareTo'" "true"

class A<T>(val n: T)

fun test() {
    A(1) <caret>>= 2
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix