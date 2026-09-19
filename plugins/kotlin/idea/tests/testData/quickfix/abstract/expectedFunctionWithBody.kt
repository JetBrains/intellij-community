// "Remove function body" "true"
// ENABLE_MULTIPLATFORM
// K2_ERROR: EXPECTED_DECLARATION_WITH_BODY
<caret>expect fun foo() {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveFunctionBodyFix
