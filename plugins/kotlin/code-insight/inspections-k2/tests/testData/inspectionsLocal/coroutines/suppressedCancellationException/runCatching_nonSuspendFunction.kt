// WITH_COROUTINES
// PROBLEM: none
package test

fun compute(raw: String): Int? {
    return <caret>runCatching {
        raw.toInt()
    }.getOrNull()
}
