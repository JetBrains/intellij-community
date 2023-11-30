// "Create extension function 'A.Companion.foo'" "true"

class A<T>(val n: T)

fun test() {
    val a: Int = A.<caret>foo(2)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix