// WITH_COROUTINES
// PROBLEM: none
package test

// No suspend context at all, nothing can be cancelled here
fun compute(raw: String): Int {
    return try {
        raw.toInt()
    } catch (<caret>e: Exception) {
        0
    }
}
