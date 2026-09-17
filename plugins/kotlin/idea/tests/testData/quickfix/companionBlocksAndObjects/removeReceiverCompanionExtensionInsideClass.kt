// "Remove the receiver" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
// K2_ERROR: UNRESOLVED_REFERENCE
class Example {
    fun m(e: Example) {
        e.b<caret>az()
    }
}

companion fun Example.baz() {}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.RemoveInstanceReceiverForCompanionMemberFix
