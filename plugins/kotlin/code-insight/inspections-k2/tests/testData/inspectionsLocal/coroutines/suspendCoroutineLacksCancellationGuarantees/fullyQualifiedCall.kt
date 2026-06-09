// WITH_COROUTINES
// PROBLEM: 'suspendCoroutine' lacks cancellation guarantees; prefer 'kotlinx.coroutines.suspendCancellableCoroutine' for proper cancellation support
// FIX: Replace with more cancellation-friendly 'suspendCancellableCoroutine'
package test

suspend fun usage(): String {
    return kotlin.coroutines.suspendCoroutine<caret> { }
}
