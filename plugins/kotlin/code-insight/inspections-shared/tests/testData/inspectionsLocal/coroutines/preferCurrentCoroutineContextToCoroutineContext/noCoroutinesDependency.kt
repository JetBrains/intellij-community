// WITH_STDLIB
// PROBLEM: none
package test

import kotlin.coroutines.coroutineContext

suspend fun test() {
    <caret>coroutineContext
}