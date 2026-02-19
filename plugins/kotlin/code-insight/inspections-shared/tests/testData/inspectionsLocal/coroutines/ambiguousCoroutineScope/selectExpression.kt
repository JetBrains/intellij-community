// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.selects.select

fun CoroutineScope.doStuff() {}

suspend fun CoroutineScope.test() {
    select<Unit> {
        <caret>doStuff()
    }
}