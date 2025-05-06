// WITH_COROUTINES
// PROBLEM: Suspicious implicit 'CoroutineScope' receiver access in suspending context
// FIX: none
package test

import kotlinx.coroutines.CoroutineScope

private suspend fun suspendWrapper(action: suspend () -> Unit) {
    action()
}

fun <T : CoroutineScope> T.doGenericStuff() {}

suspend fun CoroutineScope.test() {
    suspendWrapper {
        <caret>doGenericStuff()
    }
}