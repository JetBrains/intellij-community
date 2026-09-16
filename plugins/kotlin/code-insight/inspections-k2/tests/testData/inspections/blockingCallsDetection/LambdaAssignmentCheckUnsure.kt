// CONSIDER_UNKNOWN_AS_BLOCKING: true
// CONSIDER_SUSPEND_CONTEXT_NON_BLOCKING: false
@file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER")

import kotlin.coroutines.*
import java.lang.Thread.sleep

class LambdaAssignmentCheckUnsure {
    val returnSuspendProperty: suspend () -> Unit = {
        Thread.sleep(1)
    }

    fun returnSuspend(): suspend () -> Unit = {
        Thread.sleep(1)
    }

    val returnSuspendPropertyAccessor: suspend () -> Unit
        get() = {
            Thread.sleep(1)
        }

    fun assignToSuspendType() {
        val suspendType: suspend () -> Unit = {
            Thread.sleep(2)
        }
    }

    fun assignToSuspendTypeParenthesized() {
        val parenthesized: suspend () -> Unit = ({
            Thread.sleep(4)
        })
    }

    fun assignToSuspendTypeLabeled() {
        val labeled: suspend () -> Unit = lambda@{
            Thread.sleep(5)
        }
    }

    fun suspendTypedParameterDefault(
        action: suspend () -> Unit = {
            Thread.sleep(6)
        }
    ) {
    }

    fun assignToSuspendTypeWithInlineWrapper() {
        val withWrapper: suspend () -> Unit = {
            run {
                Thread.sleep(7)
            }
        }
    }

    fun assignToSuspendTypeWithNonInlineLambda() {
        //no warning should be present, the inner lambda is neither inlined nor of a suspend type
        val withInnerLambda: suspend () -> Unit = {
            customFunction {
                Thread.sleep(8)
            }
        }
    }

    fun assignToNonSuspendType() {
        //no warning should be present, the target type is not a suspend function type
        val plain: () -> Unit = {
            Thread.sleep(9)
        }
    }

    fun returnNonSuspend(): () -> Unit = {
        //no warning should be present, the return type is not a suspend function type
        Thread.sleep(10)
    }

    fun nonSuspendTypedParameterDefault(
        //no warning should be present, the parameter type is not a suspend function type
        action: () -> Unit = {
            Thread.sleep(11)
        }
    ) {
    }

    fun returnSuspendFromBlockBody(): suspend () -> Unit {
        return {
            Thread.sleep(12)
        }
    }

    val returnSuspendFromAccessorBlockBody: suspend () -> Unit
        get() {
            return {
                Thread.sleep(13)
            }
        }

    fun assignToSuspendTypeLocal() {
        val handler: suspend () -> Unit = {
            Thread.sleep(14)
        }
    }

    fun suspendLocalInsideUnrelatedCall() {
        customFunction {
            val handler: suspend () -> Unit = {
                Thread.sleep(15)
            }
        }
    }

    fun nonSuspendLocalInsideUnrelatedCall() {
        //no warning should be present, the target type is not a suspend function type
        customFunction {
            val plain: () -> Unit = {
                Thread.sleep(16)
            }
        }
    }

    suspend fun lambdaNotInvoked() {
        //no warning should be present
        val fn1 = { Thread.sleep(3) }
    }
}

fun customFunction(action: () -> Unit) {
    action()
}
