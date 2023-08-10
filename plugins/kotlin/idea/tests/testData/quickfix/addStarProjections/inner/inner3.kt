// "Add star projections" "true"
class A {
    class B {
        inner class C<T> {
            inner class D
            fun test(x: Any) = x is D<caret>
        }
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddStartProjectionsForInnerClass