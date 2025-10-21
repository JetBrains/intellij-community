// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred

suspend fun CoroutineScope.asyncWithRequireNotNull() {
    val d2: Deferred<Int>? = async { 42 }
    // Should not be flagged - requireNotNull is in exclusion list
    <caret>requireNotNull(d2)
}