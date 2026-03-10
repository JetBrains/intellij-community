// "Remove function body" "true"
// K2_ERROR: Function 'foo' with a body cannot be abstract.
abstract class A() {
    <caret>abstract fun foo() = /*1*/
            { "" /*2*/ } // 3
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveFunctionBodyFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveFunctionBodyFix