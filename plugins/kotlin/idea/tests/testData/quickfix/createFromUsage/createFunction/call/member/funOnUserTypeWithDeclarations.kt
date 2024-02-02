// "Create member function 'F.foo'" "true"
class F {
    fun bar() {

    }
}

class X {
    val f: Int = F().<caret>foo(1, "2")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix