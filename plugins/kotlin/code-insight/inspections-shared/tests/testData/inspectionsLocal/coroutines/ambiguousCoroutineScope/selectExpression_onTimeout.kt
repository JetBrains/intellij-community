// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.selects.onTimeout

fun CoroutineScope.doStuff() {}

suspend fun CoroutineScope.test() {
    select<Unit> {
        onTimeout(10L) {
            <caret>doStuff()
        }
    }
}