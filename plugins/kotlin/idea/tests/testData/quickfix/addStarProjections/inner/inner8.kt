// "Add star projections" "true"
class A<T, U> {
    inner class B<V, W> {
        inner class C<X, Y>
        fun test(x: Any) = x is C<caret>
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddStartProjectionsForInnerClass