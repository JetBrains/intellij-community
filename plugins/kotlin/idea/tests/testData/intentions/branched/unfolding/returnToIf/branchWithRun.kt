// WITH_STDLIB

fun test(cond: Boolean): String {
    <caret>return if (cond) {
        "Hello"
    } else {
        run { error("This is bad") }
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.branchedTransformations.intentions.UnfoldReturnToIfIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.UnfoldReturnToIfIntention