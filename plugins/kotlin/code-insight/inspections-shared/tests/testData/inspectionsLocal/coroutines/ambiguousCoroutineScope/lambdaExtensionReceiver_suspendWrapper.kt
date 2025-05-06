// WITH_COROUTINES
// PROBLEM: Suspicious implicit 'CoroutineScope' receiver access in suspending context
// FIX: none
package test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope

private suspend fun suspendWrapper(action: suspend () -> Unit) {
    action()
}

fun CoroutineScope.doStuff() {}

suspend fun test() {
    coroutineScope {
        suspendWrapper {
            <caret>doStuff()
        }
    }
}