// "Convert to block body" "true"
// K2_ERROR: RETURN_IN_FUNCTION_WITH_EXPRESSION_BODY
fun foo(): Int = when {
    true -> {
        if (true) <caret>return 1
        bar()
        2
    }
    else -> 3
}

fun bar() {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertToBlockBodyFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertToBlockBodyFix
// LANGUAGE_VERSION: 2.2