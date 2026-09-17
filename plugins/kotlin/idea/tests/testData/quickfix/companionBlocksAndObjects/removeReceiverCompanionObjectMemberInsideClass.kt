// "Remove the receiver" "true"
// K2_ERROR: UNRESOLVED_REFERENCE
class Example {
    companion object {
        fun test() {}
    }
    fun m(e: Example) {
        e.tes<caret>t()
    }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.RemoveInstanceReceiverForCompanionMemberFix
