// WITH_COROUTINES
// PROBLEM: 'suspendCoroutine' lacks cancellation guarantees; prefer 'kotlinx.coroutines.suspendCancellableCoroutine' for proper cancellation support
// FIX: Replace with more cancellation-friendly 'suspendCancellableCoroutine'
package test

import kotlin.coroutines.suspendCoroutine

suspend fun foo(): String {
    return <caret>suspendCoroutine {}
}
