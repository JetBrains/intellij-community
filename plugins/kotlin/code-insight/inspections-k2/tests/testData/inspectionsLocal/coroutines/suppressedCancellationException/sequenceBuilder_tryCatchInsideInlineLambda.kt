// WITH_COROUTINES
// PROBLEM: none
package test

fun numbers(values: List<Int>): Sequence<Int> = sequence {
    values.forEach {
        try {
            yield(it)
        } catch (<caret>e: Exception) {
        }
    }
}
