// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CoroutineScope

private suspend inline fun suspendInlineWrapper(action: suspend () -> Unit) {
    action()
}

fun CoroutineScope.doStuff() {}

suspend fun CoroutineScope.test() {
    suspendInlineWrapper {
        <caret>doStuff()
    }
}