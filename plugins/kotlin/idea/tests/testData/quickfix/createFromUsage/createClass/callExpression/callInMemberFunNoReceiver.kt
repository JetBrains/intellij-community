// "Create class 'Foo'" "true"

class A {
    class B {
        fun test() = <caret>Foo(2, "2")
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix