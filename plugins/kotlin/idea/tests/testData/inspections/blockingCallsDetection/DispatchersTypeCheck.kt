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

    withContext(Dispatchers.IO + CoroutineName("My coroutine")) {
        //no warning since IO dispatcher type used
        Thread.sleep(42)
    }
}