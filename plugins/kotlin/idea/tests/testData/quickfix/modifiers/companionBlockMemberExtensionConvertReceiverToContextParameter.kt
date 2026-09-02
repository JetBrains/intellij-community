// "Convert receiver to context parameter" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -Xcontext-parameters
// K2_ERROR: COMPANION_BLOCK_MEMBER_EXTENSION
class Example {
    companion {
        fun <caret>String.foo(): Int = length
    }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.CompanionBlockMemberExtensionFixFactory$ConvertReceiverToContextParameterFix
