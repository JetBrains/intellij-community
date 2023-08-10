// "Create extension property 'A.foo'" "true"
class A(val n: Int)

class B {
    val A.test: Boolean get() = <caret>foo
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix