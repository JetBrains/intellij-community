// WITH_COROUTINES
// PROBLEM: none
package test

fun numbers(): Sequence<Int> = sequence {
    try {
        yield(1)
    } catch (<caret>e: Exception) {
    }
}
