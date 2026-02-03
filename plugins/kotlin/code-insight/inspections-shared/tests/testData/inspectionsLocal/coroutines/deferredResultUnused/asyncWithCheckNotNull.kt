// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred

suspend fun CoroutineScope.asyncWithCheckNotNull() {
    val d3: Deferred<Int>? = async { 42 }
    // Should not be flagged - checkNotNull is in exclusion list
    <caret>checkNotNull(d3)
}