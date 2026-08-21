// CONSIDER_UNKNOWN_AS_BLOCKING: true
// CONSIDER_SUSPEND_CONTEXT_NON_BLOCKING: false
@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")

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

    // Locally inlined lambdas run in the caller's frame, so they inherit its suspend context

    suspend fun inlineLambda() {
        run {
            Thread.sleep(5)
        }
    }

    suspend fun nestedInlineLambdas() {
        run {
            listOf(1).forEach {
                Thread.sleep(6)
            }
        }
    }

    suspend fun inlineLambdaWithReceiver() {
        "text".let {
            Thread.sleep(7)
        }
    }

    suspend fun arrayGeneratorLambda() {
        Array(1) { index ->
            Thread.sleep(8)
            "$index"
        }
    }

    fun inlineLambdaInNonSuspendFunction() {
        //no warning should be present, the enclosing function is not suspend
        run {
            Thread.sleep(9)
        }
    }

    // Lambdas which are not locally inlined form their own context

    suspend fun nonInlineLambda() {
        //no warning should be present, the lambda is not inlined and its type is not suspend
        customFunction {
            Thread.sleep(10)
        }
    }

    suspend fun nonInlineLambdaWithInlineWrapper() {
        //no warning should be present, the inlined lambda belongs to the non-suspend lambda
        customFunction {
            run {
                Thread.sleep(11)
            }
        }
    }

    fun suspendLambda() {
        customSuspendFunction {
            Thread.sleep(12)
        }
    }

    fun suspendLambdaWithInlineWrapper() {
        customSuspendFunction {
            run {
                Thread.sleep(13)
            }
        }
    }

    suspend fun noinlineLambda() {
        //no warning should be present, a noinline parameter is not inlined into the caller
        withNoinlineBlock {
            Thread.sleep(14)
        }
    }

    suspend fun crossinlineLambda() {
        //no warning should be present, a crossinline parameter may be executed outside of the caller
        withCrossinlineBlock {
            Thread.sleep(15)
        }
    }

    fun crossinlineSuspendLambda() {
        withCrossinlineSuspendBlock {
            Thread.sleep(16)
        }
    }

    // Nested declarations are always a context boundary

    suspend fun localFunction() {
        //no warning should be present, a non-suspend local function is a separate context
        fun local() {
            Thread.sleep(17)
        }
        local()
    }

    suspend fun localSuspendFunction() {
        suspend fun local() {
            Thread.sleep(18)
        }
        local()
    }

    suspend fun lambdaNotInvoked() {
        //no warning should be present
        val fn = { Thread.sleep(19) }
    }
}

fun customFunction(action: () -> Unit) {
    action()
}

fun customSuspendFunction(action: suspend () -> Unit) {
}

@Suppress("NOTHING_TO_INLINE")
inline fun withNoinlineBlock(noinline block: () -> Unit) {
    customFunction(block)
}

inline fun withCrossinlineBlock(crossinline block: () -> Unit) {
    customFunction { block() }
}

inline fun withCrossinlineSuspendBlock(crossinline block: suspend () -> Unit) {
    customSuspendFunction { block() }
}
