// "Replace the receiver with 'Example'" "true"
// K2_ERROR: UNRESOLVED_REFERENCE
class Example {
    companion object {
        fun test() {}
    }
}

fun m(e: Example) {
    e.tes<caret>t()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ReplaceInstanceReceiverWithClassNameFix
