// "Create member function 'A.unaryMinus'" "true"

class A<T>(val n: T) {
    operator fun minus(n: Int): A<T> = throw Exception()
}

fun test() {
    val a: A<Int> = <caret>-A(1)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix