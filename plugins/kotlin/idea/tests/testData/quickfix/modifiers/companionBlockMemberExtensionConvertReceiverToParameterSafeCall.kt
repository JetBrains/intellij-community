// "Convert receiver to parameter" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks
// K2_ERROR: COMPANION_BLOCK_MEMBER_EXTENSION
class Example {
    fun m(s: String?): Int? = s?.bar()

    companion {
        fun String.<caret>bar(): Int = length
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.CompanionBlockMemberExtensionFixFactory$ConvertReceiverToParameterFix
