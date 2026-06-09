// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CoroutineScope

fun CoroutineScope.test() {
    <caret>coroutineContext
}