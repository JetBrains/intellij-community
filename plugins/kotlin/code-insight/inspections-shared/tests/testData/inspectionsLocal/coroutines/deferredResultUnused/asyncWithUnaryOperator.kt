// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred

operator fun Deferred<Int>.unaryPlus() = this

fun CoroutineScope.asyncWithUnaryOperator() {
    // Should not be flagged - result is used with unary operator
    <caret>+(async { 0 })
}