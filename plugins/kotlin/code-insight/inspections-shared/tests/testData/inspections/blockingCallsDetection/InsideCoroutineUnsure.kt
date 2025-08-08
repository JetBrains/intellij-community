// CONSIDER_UNKNOWN_AS_BLOCKING: true
// CONSIDER_SUSPEND_CONTEXT_NON_BLOCKING: false
@file:Suppress("UNUSED_PARAMETER")

import kotlin.coroutines.*
import java.lang.Thread

class InsideCoroutineUnsure {
    suspend fun example1() {
        Thread.sleep(1)
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
            Thread.sleep(4)
        })
    }
}