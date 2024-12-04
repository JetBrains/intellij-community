fun test(b: Boolean): Int {
    <caret>return if (b) {
        throw AssertionError()
    } else {
        1
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.branchedTransformations.intentions.UnfoldReturnToIfIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.UnfoldReturnToIfIntention