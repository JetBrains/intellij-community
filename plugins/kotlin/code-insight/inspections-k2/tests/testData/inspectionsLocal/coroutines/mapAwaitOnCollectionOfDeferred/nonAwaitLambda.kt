// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.Deferred

suspend fun takeWithTransformation(asyncList: List<Deferred<Int>>) {
    val results = asyncList.<caret>map { it.await().toString() }
    println(results)
}