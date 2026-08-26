// CONSIDER_UNKNOWN_AS_BLOCKING: true
// CONSIDER_SUSPEND_CONTEXT_NON_BLOCKING: true
@file:Suppress("UNUSED_PARAMETER")
package kotlinx.coroutines

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.coroutines.CoroutineContext
import java.lang.Thread

suspend fun dispatcherStates() {
    // no dispatcher argument to inspect at all, so the enclosing suspend context decides
    customSuspendFunction {
        Thread.<warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">sleep</warning>(1)
    }

    //no warning since the dispatcher is present but cannot be classified
    withContext(getUnknownContext()) {
        Thread.sleep(2)
    }

    //no warning since IO dispatcher type used
    withContext(Dispatchers.IO) {
        Thread.sleep(3)
    }

    withContext(Dispatchers.Default) {
        Thread.<warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">sleep</warning>(4)
    }
}

fun flowWithoutFlowOn(): Flow<Int> = flow {
    Thread.<warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">sleep</warning>(5)
    emit(1)
}

//no warning since IO dispatcher type used
fun flowOnIo(): Flow<Int> = flow {
    Thread.sleep(6)
    emit(1)
}.flowOn(Dispatchers.IO)

fun flowOnMain(): Flow<Int> = flow {
    Thread.<warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">sleep</warning>(7)
    emit(1)
}.flowOn(Dispatchers.Main)

fun getUnknownContext(): CoroutineContext = TODO()

fun customSuspendFunction(action: suspend () -> Unit) {
}
