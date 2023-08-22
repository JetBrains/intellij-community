// "Remove function body" "true"
abstract class A() {
    <caret>abstract fun foo() : Any { return "a" }
}

/* IGNORE_FIR */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveFunctionBodyFix