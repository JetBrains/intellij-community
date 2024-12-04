// AFTER-WARNING: Parameter 'a' is never used
fun <T> doSomething(a: T) {}

fun test(n: Int): String {
    <caret>return if (n == 1) {
        doSomething("***")
        "one"
    } else {
        doSomething("***")
        "two"
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.branchedTransformations.intentions.UnfoldReturnToIfIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.UnfoldReturnToIfIntention