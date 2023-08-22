// FIR_IDENTICAL
// FIR_COMPARISON

import java.lang.Thread

fun test(thr: Thread) {
    consume(thr::checkAcc<caret>)
}

fun consume(nameFactory: () -> Unit) {}

// ELEMENT: checkAccess