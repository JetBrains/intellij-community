// "Remove function body" "true"
// ENABLE_MULTIPLATFORM
// K2_ERROR: 'expect' declaration cannot have a body.
<caret>expect fun foo() {}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveFunctionBodyFix
