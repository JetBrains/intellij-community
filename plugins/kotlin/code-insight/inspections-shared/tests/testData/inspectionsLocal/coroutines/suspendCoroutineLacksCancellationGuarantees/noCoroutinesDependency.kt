// WITH_STDLIB
// PROBLEM: none
package test

import kotlin.coroutines.suspendCoroutine

suspend fun qux(): String {
    return <caret>suspendCoroutine {}
}
