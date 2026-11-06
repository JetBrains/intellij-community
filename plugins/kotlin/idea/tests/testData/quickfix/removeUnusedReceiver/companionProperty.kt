// "Remove redundant receiver parameter" "true"
// COMPILER_ARGUMENTS: -Xcompanion-blocks-and-extensions

companion val <caret>String.x: String
    get() = ""

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.RemoveReceiverParameterFix