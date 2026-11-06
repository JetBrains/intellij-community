// "Remove redundant receiver parameter" "true"
// COMPILER_ARGUMENTS: -Xcompanion-blocks-and-extensions

private companion fun <caret>String.bar() = ""

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.RemoveReceiverParameterFix