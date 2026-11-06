// COMPILER_ARGUMENTS: -Xallow-kotlin-package
// WITH_COROUTINES
// PROBLEM: none
package kotlin.test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred

// imitates real assertNotNull function from kotlin.test
fun <T : Any> assertNotNull(value: T?): T = value!!

suspend fun CoroutineScope.asyncWithAssertNotNull() {
    val d: Deferred<Int>? = async { 42 }
    // Should not be flagged - assertNotNull is in exclusion list
    <caret>assertNotNull(d)
}