// "Remove identifier from anonymous function" "true"
// K2_ERROR: ANONYMOUS_FUNCTION_WITH_NAME

fun foo() {
    (fun bar<caret>() {
        return@bar
    })
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveNameFromFunctionExpressionFix