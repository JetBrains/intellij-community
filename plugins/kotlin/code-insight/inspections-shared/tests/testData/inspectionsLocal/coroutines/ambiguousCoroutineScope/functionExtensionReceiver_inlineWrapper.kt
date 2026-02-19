// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CoroutineScope

private inline fun inlineWrapper(action: () -> Unit) {
    action()
}

fun CoroutineScope.doStuff() {}

suspend fun CoroutineScope.test() {
    inlineWrapper {
        <caret>doStuff()
    }
}