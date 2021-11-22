@file:Suppress("UNUSED_PARAMETER")

import kotlin.coroutines.*
import java.lang.Thread.sleep

class InsideCoroutine {
    suspend fun example1() {
        Thread.<warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">sleep</warning>(1);
    }
}