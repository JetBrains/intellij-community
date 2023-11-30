// "Add star projections" "true"
class A {
    inner class B<T>
    fun test(x: Any) = x is B<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddStartProjectionsForInnerClass