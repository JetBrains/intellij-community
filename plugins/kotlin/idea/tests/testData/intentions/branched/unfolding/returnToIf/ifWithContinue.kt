fun test(b: Boolean): Int {
    var i = 0
    while (i == 0) {
        return if<caret> (b) {
            1
        } else {
            i++
            continue
        }
    }
    return 0
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.branchedTransformations.intentions.UnfoldReturnToIfIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.UnfoldReturnToIfIntention