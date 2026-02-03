// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.Deferred

suspend fun takeWithExplicitReturn(asyncList: List<Deferred<Int>>): Int {
    asyncList.<caret>map {
        return it.await()
    }

    return 0
}