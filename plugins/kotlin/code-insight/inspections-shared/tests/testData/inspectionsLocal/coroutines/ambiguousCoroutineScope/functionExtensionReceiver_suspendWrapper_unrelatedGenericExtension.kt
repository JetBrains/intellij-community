// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CoroutineScope

private suspend fun suspendWrapper(action: suspend () -> Unit) {
    action()
}

fun <T> T.doUnrelatedGenericStuff() {}

suspend fun CoroutineScope.test() {
    suspendWrapper {
        <caret>doUnrelatedGenericStuff()
    }
}