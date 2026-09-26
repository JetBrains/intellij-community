// WITH_COROUTINES
// PROBLEM: Usage of 'map { it.await() }' on 'Collection<Deferred>' instead of single 'awaitAll()'
package test

import kotlinx.coroutines.Deferred

suspend fun takeWithCustomParam(asyncList: List<Deferred<String>>) {
    val results = asyncList.<caret>map { deferred -> deferred.await() }
    println(results)
}