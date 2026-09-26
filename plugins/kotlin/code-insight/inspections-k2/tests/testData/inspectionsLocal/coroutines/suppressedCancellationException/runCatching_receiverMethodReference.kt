// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.delay

suspend fun compute(input: String): Int? {
    delay(100)
    return input.<caret>runCatching(String::toInt).getOrNull()
}
