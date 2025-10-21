// WITH_COROUTINES
// PROBLEM: 'suspendCoroutine' lacks cancellation guarantees; prefer 'kotlinx.coroutines.suspendCancellableCoroutine' for proper cancellation support
// FIX: Replace with more cancellation-friendly 'suspendCancellableCoroutine'
import kotlin.coroutines.suspendCoroutine

suspend fun quux(): String {
    return <caret>suspendCoroutine(fun (c) {
        return@suspendCoroutine
    })
}
