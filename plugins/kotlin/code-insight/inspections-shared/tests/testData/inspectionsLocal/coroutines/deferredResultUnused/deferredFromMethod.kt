// WITH_COROUTINES
// PROBLEM: 'Deferred' result is unused
// FIX: none
package test

import kotlinx.coroutines.Deferred

class User

interface DbHandler {
    fun getUser(id: Long): Deferred<User>
    fun doStuff(): Deferred<Unit>
}

fun DbHandler.test() {
    <caret>getUser(42L)
}