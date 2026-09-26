// WITH_COROUTINES
// PROBLEM: none
package test

fun numbers(): Iterator<Int> = iterator {
    try {
        yield(1)
    } catch (<caret>e: Exception) {
    }
}
