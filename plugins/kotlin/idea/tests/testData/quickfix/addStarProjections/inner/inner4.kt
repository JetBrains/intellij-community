// "Add star projections" "true"
// K2_ERROR: NO_TYPE_ARGUMENTS_ON_RHS
class A {
    class B<T> {
        inner class C<U> {
            inner class D
            fun test(x: Any) = x is D<caret>
        }
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddStartProjectionsForInnerClass
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddStarProjectionsFixFactory$AddStartProjectionsForInnerClass