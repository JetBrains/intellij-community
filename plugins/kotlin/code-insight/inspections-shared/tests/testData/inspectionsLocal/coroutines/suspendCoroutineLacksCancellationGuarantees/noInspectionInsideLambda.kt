// WITH_COROUTINES
// PROBLEM: none
package test

import kotlin.coroutines.suspendCoroutine

suspend fun foo(): String {
    return suspendCoroutine {
        <caret>run {}
    }
}
