// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.Deferred

suspend fun takeArray(asyncArray: Array<Deferred<String>>) {
    val results = asyncArray.<caret>map { it.await() }
    println(results)
}