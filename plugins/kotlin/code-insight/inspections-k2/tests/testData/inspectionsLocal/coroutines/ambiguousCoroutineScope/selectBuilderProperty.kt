// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.SelectBuilder

fun CoroutineScope.doStuff() {}

suspend fun CoroutineScope.test() {
    val deferred = async { 42 }

    val p: SelectBuilder<Unit>.() -> Unit = {
        deferred.onAwait {
            <caret>doStuff()
        }
    }
}
