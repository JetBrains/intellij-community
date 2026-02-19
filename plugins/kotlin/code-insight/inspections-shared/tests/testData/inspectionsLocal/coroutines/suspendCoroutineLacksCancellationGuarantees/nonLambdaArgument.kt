// WITH_COROUTINES
// PROBLEM: 'suspendCoroutine' lacks cancellation guarantees; prefer 'kotlinx.coroutines.suspendCancellableCoroutine' for proper cancellation support
// FIX: Replace with more cancellation-friendly 'suspendCancellableCoroutine'
package test

import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine

suspend fun foo(action: (Continuation<String>) -> Unit): String {
    return <caret>suspendCoroutine(action)
}
