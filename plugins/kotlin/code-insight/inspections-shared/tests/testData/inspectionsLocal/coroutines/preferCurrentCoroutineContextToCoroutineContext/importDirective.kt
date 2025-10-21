// WITH_COROUTINES
// PROBLEM: none
package test

import kotlin.coroutines.coroutineContext<caret>

suspend fun test() {
    coroutineContext
}