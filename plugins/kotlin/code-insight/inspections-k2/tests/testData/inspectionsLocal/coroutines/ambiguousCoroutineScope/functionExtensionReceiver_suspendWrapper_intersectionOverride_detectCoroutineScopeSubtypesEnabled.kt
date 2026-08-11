// WITH_COROUTINES
// PROBLEM: Suspicious implicit 'CoroutineScope' receiver access in suspending context
// FIX: Add explicit labeled receiver (does not change semantics)
package test

import kotlinx.coroutines.CoroutineScope

private fun fakeOuterBuilder(action: suspend CoroutineScope.() -> Unit) {}

interface ScopeWithSharedFun : CoroutineScope {
    fun sharedFun()
}

interface NotScope {
    fun sharedFun()
}

interface ScopeAndNotScopeChild : ScopeWithSharedFun, NotScope

fun ScopeAndNotScopeChild.test() {
    fakeOuterBuilder {
        <caret>sharedFun()
    }
}
