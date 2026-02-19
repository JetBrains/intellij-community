// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

suspend fun takeWithTransformation(asyncList: List<Deferred<Int>>) {
    val results = asyncList.<caret>map {
        println("hello!")
        it.await()
    }
    println(results)
}