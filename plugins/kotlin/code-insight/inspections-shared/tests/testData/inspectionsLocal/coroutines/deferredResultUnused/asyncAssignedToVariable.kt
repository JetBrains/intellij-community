// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred

fun CoroutineScope.asyncAssignedToVariable() {
    // Should not be flagged - result is assigned to variable
    val res = <caret>async { 13 }
}