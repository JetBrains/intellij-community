// "Create member function 'A.foo'" "true"
// KEEP_ACTIONS_LIST_ORDER
// K2_ACTIONS_LIST: Specify all remaining arguments by name
// K2_ACTIONS_LIST: Create extension function 'A<Int>.foo'
// K2_ACTIONS_LIST: Create member function 'A.foo'
// K2_ACTIONS_LIST: Add 'i =' to argument

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction

class A<T>(val n: T) {
    fun foo(i: Int, s: String): A<T> = throw Exception()
}

fun test() {
    val a: A<Int> = A(1).foo(2<caret>)
}
