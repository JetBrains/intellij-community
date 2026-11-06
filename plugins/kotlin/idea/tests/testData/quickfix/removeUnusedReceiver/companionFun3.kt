// "Remove redundant receiver parameter" "true"
// COMPILER_ARGUMENTS: -Xcompanion-blocks-and-extensions

companion fun <caret>String.bar(x: Int, y: String): String = y + x

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.RemoveReceiverParameterFix