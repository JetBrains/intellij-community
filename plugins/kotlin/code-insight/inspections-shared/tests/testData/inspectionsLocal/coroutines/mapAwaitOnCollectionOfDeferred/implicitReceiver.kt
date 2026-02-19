// WITH_COROUTINES
// PROBLEM: Usage of 'map { it.await() }' on 'Collection<Deferred>' instead of single 'awaitAll()'
package test

import kotlinx.coroutines.Deferred

suspend fun <T> Collection<Deferred<T>>.processDeferred(): List<T> {
    return <caret>map { it.await() }
}