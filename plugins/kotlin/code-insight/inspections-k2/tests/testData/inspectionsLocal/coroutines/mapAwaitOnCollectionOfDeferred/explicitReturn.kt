// WITH_COROUTINES
// PROBLEM: Usage of 'map { it.await() }' on 'Collection<Deferred>' instead of single 'awaitAll()'
package test

import kotlinx.coroutines.Deferred

suspend fun takeWithExplicitReturn(asyncList: List<Deferred<Int>>) {
    val results = asyncList.<caret>map {
        return@map it.await() 
    }
    println(results)
}