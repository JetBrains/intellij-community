// WITH_COROUTINES
// PROBLEM: Usage of 'kotlin.coroutine.coroutineContext' can be ambiguous
package test

import kotlin.coroutines.coroutineContext

suspend fun test() {
    <caret>coroutineContext.toString()
}