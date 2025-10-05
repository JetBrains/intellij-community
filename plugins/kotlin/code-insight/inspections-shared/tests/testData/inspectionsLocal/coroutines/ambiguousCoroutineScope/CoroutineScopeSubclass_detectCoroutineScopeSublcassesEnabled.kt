// WITH_COROUTINES
// PROBLEM: Suspicious implicit 'CoroutineScope' receiver access in suspending context
// FIX: Add explicit labeled receiver (does not change semantics)
package test

import kotlinx.coroutines.CoroutineScope

fun CoroutineScope.doStuff() {}

abstract class MyCoroutineScopeBase : CoroutineScope {
    suspend fun foo() {
        <caret>doStuff() 
    }
}