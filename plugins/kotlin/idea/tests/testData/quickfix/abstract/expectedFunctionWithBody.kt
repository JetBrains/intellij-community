// "Remove function body" "true"
// ENABLE_MULTIPLATFORM
// K2_ERROR: EXPECTED_DECLARATION_WITH_BODY
<caret>expect fun foo() {}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveFunctionBodyFix
