// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList

suspend fun takeFlow(asyncFlow: Flow<Deferred<String>>) {
    val results = asyncFlow.<caret>map { it.await() }
    println(results.toList())
}