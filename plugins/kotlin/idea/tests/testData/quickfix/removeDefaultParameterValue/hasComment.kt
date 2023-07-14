// "Remove default parameter value" "true"
open class A {
    open fun foo(x: Int, y: Int) {}
}

class B : A() {
    override fun foo(x : Int /* comment1 */ = /* comment2 */ 1<caret>, y: Int) {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveDefaultParameterValueFix