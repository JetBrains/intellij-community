// "Remove identifier from anonymous function" "true"
// K2_ERROR: Anonymous functions with names are prohibited.

fun foo() {
    (fun bar<caret>() {
        return@bar
    })
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveNameFromFunctionExpressionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveNameFromFunctionExpressionFix