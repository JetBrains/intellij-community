// "Remove redundant receiver parameter" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks
// K2_ERROR: COMPANION_BLOCK_MEMBER_EXTENSION
class Example {
    companion {
        fun <caret>Int.foo(): Int = 42
    }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.RemoveReceiverParameterFix
