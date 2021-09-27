@file:Suppress("UNUSED_PARAMETER")

import kotlin.coroutines.*
import org.jetbrains.annotations.BlockingExecutor
import org.jetbrains.annotations.NonBlockingExecutor
import java.lang.Thread

suspend fun testFunction() {
    @BlockingExecutor
    val ctx = getContext()
    // no warnings with @BlockingContext annotation on ctx object
    withContext(ctx) { Thread.sleep(1) }

    // no warnings with @BlockingContext annotation on getContext() method
    withContext(getContext()) { Thread.sleep(2) }

    withContext(getNonBlockingContext()) {
        Thread.<warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">sleep</warning>(3)
    }

    withContext(getExplicitlyNonBlockingContext()) {
        Thread.<warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">sleep</warning>(4)
    }
}

@BlockingExecutor
fun getContext(): CoroutineContext = TODO()

fun getNonBlockingContext(): CoroutineContext = TODO()

@NonBlockingExecutor
fun getExplicitlyNonBlockingContext(): CoroutineContext = TODO()

suspend fun <T> withContext(
    context: CoroutineContext,
    f: suspend () -> T
) {
    TODO()
}