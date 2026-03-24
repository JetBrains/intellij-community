// "Add star projections" "true"
// K2_ERROR: One type argument expected. Use 'A.B<*>' if you do not intend to pass type arguments.
class A {
    inner class B<T>
    fun test(x: Any) = x is B<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddStartProjectionsForInnerClass
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddStarProjectionsFixFactory$AddStartProjectionsForInnerClass