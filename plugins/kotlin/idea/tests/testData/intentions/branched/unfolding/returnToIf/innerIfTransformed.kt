// AFTER-WARNING: Parameter 'a' is never used
fun <T> doSomething(a: T) {}

fun test(n: Int): String {
    if (n == 1) {
        <caret>return if (3 > 2) {
            doSomething("***")
            "one"
        } else {
            doSomething("***")
            "???"
        }
    } else if (n == 2) {
        doSomething("***")
        return "two"
    } else {
        doSomething("***")
        return "too many"
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.branchedTransformations.intentions.UnfoldReturnToIfIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.UnfoldReturnToIfIntention