// CONSIDER_UNKNOWN_AS_BLOCKING: false
// CONSIDER_SUSPEND_CONTEXT_NON_BLOCKING: true
@file:Suppress("UNUSED_PARAMETER")

import kotlin.coroutines.*
import java.lang.Thread

class InsideCoroutine {
    suspend fun example1() {
        Thread.<warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">sleep</warning>(1)
    }

    fun example2() {
        Thread.sleep(2)
    }

    fun example3() {
        run {
            Thread.sleep(3)
        }
    }

    suspend fun example4() {
        run(fun() {
            Thread.<warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">sleep</warning>(4)
        })
    }
}