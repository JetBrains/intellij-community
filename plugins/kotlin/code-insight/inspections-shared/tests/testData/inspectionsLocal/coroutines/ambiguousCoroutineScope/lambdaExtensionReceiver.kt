// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope

fun CoroutineScope.doStuff() {}

suspend fun test() {
    coroutineScope {
        <caret>doStuff()
    }
}