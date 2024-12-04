// DISABLE-ERRORS

fun test(b: Boolean): Int {
    return<caret> if (b) {
        1
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.branchedTransformations.intentions.UnfoldReturnToIfIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.UnfoldReturnToIfIntention