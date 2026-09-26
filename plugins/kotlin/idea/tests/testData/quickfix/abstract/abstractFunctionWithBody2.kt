// "Remove function body" "true"
// K2_ERROR: ABSTRACT_FUNCTION_WITH_BODY
abstract class A() {
    <caret>abstract fun foo() : Any { return "a" }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveFunctionBodyFix