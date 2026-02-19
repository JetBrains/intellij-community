// "Remove function body" "true"
abstract class A() {
    <caret>abstract fun foo() : Any { return "a" }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveFunctionBodyFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveFunctionBodyFix