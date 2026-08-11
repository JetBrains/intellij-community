// CONSIDER_UNKNOWN_AS_BLOCKING: true
// CONSIDER_SUSPEND_CONTEXT_NON_BLOCKING: false
@file:Suppress("UNUSED_VARIABLE")

import kotlin.coroutines.*
import java.lang.Thread.sleep

class LambdaAssignmentCheckUnsure {
    fun returnSuspend(): suspend () -> Unit = {
        Thread.sleep(1)
    }

    fun assignToSuspendType() {
        val suspendType: suspend () -> Unit = {
            Thread.sleep(2)
        }
    }

    suspend fun lambdaNotInvoked() {
        //no warning should be present
        val fn1 = { Thread.sleep(3) }
    }
}