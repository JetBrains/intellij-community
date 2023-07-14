// "Create extension property 'A.foo'" "true"
class A(val n: Int)

class B {
    var A.test: Boolean
        get() = foo
        set(v: Boolean) {
            <caret>foo = v
        }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix