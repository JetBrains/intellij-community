// WITH_COROUTINES
// PROBLEM: none
package test

suspend fun foo(): String {
    return <caret>kotlin.coroutines.suspendCoroutine { }
}
