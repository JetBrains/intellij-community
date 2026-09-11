// "Add function body" "true"
// K2_ERROR: NON_ABSTRACT_FUNCTION_WITH_NO_BODY
class A() {
    fun <caret>foo()
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionBodyFix