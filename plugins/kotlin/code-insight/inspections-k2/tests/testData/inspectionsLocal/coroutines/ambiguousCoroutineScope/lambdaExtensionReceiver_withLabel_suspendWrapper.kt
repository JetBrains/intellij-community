// WITH_COROUTINES
// PROBLEM: Suspicious implicit 'CoroutineScope' receiver access in suspending context
// FIX: Add explicit labeled receiver (does not change semantics)
package test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope

private suspend fun suspendWrapper(action: suspend () -> Unit) {
    action()
}

fun CoroutineScope.doStuff() {}

suspend fun test() {
    coroutineScope customLabel@{
        suspendWrapper {
            <caret>doStuff()
        }
    }
}