@file:Suppress("UNUSED_PARAMETER")

import kotlin.coroutines.*
import org.jetbrains.annotations.BlockingContext
import org.jetbrains.annotations.NonBlockingContext
import java.lang.Thread.sleep

suspend fun testFunction() {
    @BlockingContext
    val ctx = getContext()
    // no warnings with @BlockingContext annotation on ctx object
    withContext(ctx) { Thread.sleep(2) }

    // no warnings with @BlockingContext annotation on getContext() method
    withContext(getContext()) { Thread.sleep(3) }

    withContext(getNonBlockingContext()) {
        Thread.< warning descr = "Possibly blocking call in non-blocking context could lead to thread starvation" > sleep < / warning >(3);
    }

    withContext(getExplicitlyNonBlockingContext()) {
        Thread.< warning descr = "Possibly blocking call in non-blocking context could lead to thread starvation" > sleep < / warning >(4);
    }
}

@BlockingContext
fun getContext(): CoroutineContext = TODO()

fun getNonBlockingContext(): CoroutineContext = TODO()

@NonBlockingContext
fun getExplicitlyNonBlockingContext(): CoroutineContext = TODO()

suspend fun <T> withContext(
    context: CoroutineContext,
    f: suspend () -> T
) {
    TODO()
}