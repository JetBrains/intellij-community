// WITH_COROUTINES
// PROBLEM: Usage of 'kotlin.coroutine.coroutineContext' can be ambiguous
package test

suspend fun test() {
    <caret>kotlin.coroutines.coroutineContext
}