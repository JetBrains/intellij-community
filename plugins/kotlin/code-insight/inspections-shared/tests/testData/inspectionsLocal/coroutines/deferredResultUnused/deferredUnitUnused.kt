// WITH_COROUTINES
// PROBLEM: 'Deferred' result is unused
// FIX: none
package test

import kotlinx.coroutines.Deferred

interface DbHandler {
    fun doStuff(): Deferred<Unit>
}

fun DbHandler.test() {
    <caret>doStuff()
}