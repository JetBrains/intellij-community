// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.Deferred

class User

interface DbHandler {
    fun getUser(id: Long): Deferred<User>
}

suspend fun DbHandler.test() {
    // Should not be flagged - result is used with await
    val user = <caret>getUser(42L).await()
}