// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CoroutineScope

fun interface CustomLambda {
    fun customInvoke(): Unit
}

private suspend fun suspendWrapper(action: CustomLambda) {
    action.customInvoke()
}

fun CoroutineScope.doStuff() {}

suspend fun CoroutineScope.test() {
    suspendWrapper(CustomLambda {
        <caret>doStuff()
    })
}