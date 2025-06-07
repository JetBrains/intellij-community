// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CoroutineScope

fun CoroutineScope.doStuff() {}

abstract class MyCoroutineScopeBase : CoroutineScope {
    suspend fun foo() {
        <caret>doStuff()
    }
}