// CONSIDER_UNKNOWN_AS_BLOCKING: true
// CONSIDER_SUSPEND_CONTEXT_NON_BLOCKING: false
@file:Suppress("UNUSED_PARAMETER")

import kotlin.concurrent.thread
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

suspend fun myDelay(timeInMillis: Long) {
    suspendCoroutine { continuation: Continuation<Unit> ->
        Thread {
            Thread.sleep(timeInMillis)
            continuation.resume(Unit)
        }.start()
    }
}