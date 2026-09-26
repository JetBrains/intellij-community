// "Remove the receiver" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
// K2_ERROR: UNRESOLVED_REFERENCE
class Example {
    fun m(e: Example) {
        e.te<caret>st()
    }

    companion {
        fun test() {}
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.RemoveInstanceReceiverForCompanionMemberFix
