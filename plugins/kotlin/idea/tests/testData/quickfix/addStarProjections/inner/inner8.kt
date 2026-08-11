// "Add star projections" "true"
// K2_ERROR: NO_TYPE_ARGUMENTS_ON_RHS
class A<T, U> {
    inner class B<V, W> {
        inner class C<X, Y>
        fun test(x: Any) = x is C<caret>
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddStartProjectionsForInnerClass
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddStarProjectionsFixFactory$AddStartProjectionsForInnerClass