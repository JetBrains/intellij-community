// WITH_COROUTINES
// PROBLEM: Usage of 'map { it.await() }' on 'Collection<Deferred>' instead of single 'awaitAll()'
package test

import kotlinx.coroutines.Deferred

suspend fun takeWithTransformation(asyncList: List<Deferred<Int>>) {
    val results = asyncList.<caret>map { it.await() }
    println(results)
}