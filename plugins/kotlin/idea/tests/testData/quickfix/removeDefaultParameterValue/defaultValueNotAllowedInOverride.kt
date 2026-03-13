// "Remove default parameter value" "true"
// K2_ERROR: An overriding function is not allowed to specify default values for its parameters.
open class A {
    open fun foo(x: Int, y: Int) {}
}

class B : A() {
    override fun foo(x : Int = 1<caret>, y: Int) {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveDefaultParameterValueFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveDefaultParameterValueFix