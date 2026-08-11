// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CoroutineScope

private fun fakeOuterBuilder(action: suspend CoroutineScope.() -> Unit) {}

interface NotScope {
    fun notScopeFun()
}

interface ScopeAndNotScopeChild : CoroutineScope, NotScope

fun ScopeAndNotScopeChild.test() {
    fakeOuterBuilder {
        <caret>notScopeFun()
    }
}
