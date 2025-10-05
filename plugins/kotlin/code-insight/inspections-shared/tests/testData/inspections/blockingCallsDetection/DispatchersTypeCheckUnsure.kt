// CONSIDER_UNKNOWN_AS_BLOCKING: true
// CONSIDER_SUSPEND_CONTEXT_NON_BLOCKING: false
@file:Suppress("UNUSED_PARAMETER")
package kotlinx.coroutines

import java.lang.Thread
import java.lang.Thread.sleep

suspend fun withIoDispatcher() {
    withContext(Dispatchers.IO) {
        //no warning since IO dispatcher type used
        Thread.sleep(42)
    }

    withContext(Dispatchers.Default) {
        Thread.<warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">sleep</warning>(1)
    }
}