// "Add star projections" "true"
class A {
    class B<T> {
        inner class C<U> {
            inner class D
        }
    }
    fun test(x: Any) = x is B.C.D<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddStartProjectionsForInnerClass