// "Create extension function 'Int.foo'" "true"
// WITH_STDLIB

class A<T>(val n: T)

fun test() {
    val a: A<Int> = 2.<caret>foo(A(1))
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix