// WITH_COROUTINES
// PROBLEM: none
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
            this.<caret>doStuff()
        }
    }
}