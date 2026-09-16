// WITH_COROUTINES
// PROBLEM: none
package test

suspend fun SequenceScope<Int>.emitFirst() {
    try {
        yield(1)
    } catch (<caret>e: Exception) {
    }
}
