// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred

operator fun Deferred<Int>.plus(arg: Int) = this

fun CoroutineScope.asyncWithBinaryOperator() {
    // Should not be flagged - result is used with binary operator
    <caret>async { -1 } + 1
}