// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred

fun useIt(d: Deferred<Int>) {}

fun CoroutineScope.asyncPassedAsArgument() {
    // Should not be flagged - result is passed as argument
    useIt(<caret>async { 7 })
}