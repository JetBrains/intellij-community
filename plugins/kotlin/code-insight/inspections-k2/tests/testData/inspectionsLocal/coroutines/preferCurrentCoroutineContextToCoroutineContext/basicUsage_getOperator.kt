// WITH_COROUTINES
// PROBLEM: Usage of 'kotlin.coroutine.coroutineContext' can be ambiguous
package test

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Job

suspend fun test() {
    <caret>coroutineContext[Job]
}