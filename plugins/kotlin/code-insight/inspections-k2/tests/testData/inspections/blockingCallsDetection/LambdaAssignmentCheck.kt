// CONSIDER_UNKNOWN_AS_BLOCKING: false
// CONSIDER_SUSPEND_CONTEXT_NON_BLOCKING: true
@file:Suppress("UNUSED_VARIABLE")

import kotlin.coroutines.*
import java.lang.Thread.sleep

class LambdaAssignmentCheck {
    fun returnSuspend(): suspend () -> Unit = {
        Thread.<warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">sleep</warning>(1)
    }

    fun assignToSuspendType() {
        val suspendType: suspend () -> Unit = {
            Thread.<warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">sleep</warning>(2)
        }
    }

    suspend fun lambdaNotInvoked() {
        //no warning should be present
        val fn1 = { Thread.sleep(3) }
    }
}