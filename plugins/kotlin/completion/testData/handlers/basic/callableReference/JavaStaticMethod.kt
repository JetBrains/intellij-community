// FIR_IDENTICAL
// FIR_COMPARISON

import java.lang.Thread

fun test() {
    consume(Thread::cur<caret>)
}

fun consume(threadFactory: () -> Thread) {}

// ELEMENT: currentThread