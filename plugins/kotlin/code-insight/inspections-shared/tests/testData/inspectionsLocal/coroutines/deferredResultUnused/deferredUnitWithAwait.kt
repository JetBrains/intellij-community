// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.Deferred

interface DbHandler {
    fun doStuff(): Deferred<Unit>
}

suspend fun DbHandler.test() {
    // Should not be flagged - result is used with await
    <caret>doStuff().await()
}