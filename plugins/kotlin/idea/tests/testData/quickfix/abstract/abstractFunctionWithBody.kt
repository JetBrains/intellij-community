// "Remove function body" "true"
// K2_ERROR: ABSTRACT_FUNCTION_WITH_BODY
abstract class A() {
    <caret>abstract fun foo() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveFunctionBodyFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveFunctionBodyFix