// WITH_COROUTINES
// PROBLEM: Usage of 'kotlin.coroutine.coroutineContext' can be ambiguous
package test

import kotlinx.coroutines.Job

suspend fun test() {
    <caret>kotlin.coroutines.coroutineContext.toString()
}