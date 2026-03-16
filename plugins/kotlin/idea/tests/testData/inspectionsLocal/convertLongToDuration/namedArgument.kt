// WITH_COROUTINES
// PROBLEM: none

import kotlinx.coroutines.delay

suspend fun test() {
    de<caret>lay(timeMillis = 1000)
}