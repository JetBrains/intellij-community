// "Remove identifier from anonymous function" "true"

fun foo() {
    (fun bar<caret>() {
        return@bar
    })
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveNameFromFunctionExpressionFix