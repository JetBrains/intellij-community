// CONSIDER_UNKNOWN_AS_BLOCKING: false
// CONSIDER_SUSPEND_CONTEXT_NON_BLOCKING: true
@file:Suppress("UNUSED_PARAMETER")

import kotlin.coroutines.*
import org.jetbrains.annotations.BlockingExecutor
import org.jetbrains.annotations.NonBlockingExecutor
import java.lang.Thread

suspend fun testFunction() {
    // no warnings, type is annotated with @BlockingExecutor
    withContext(CustomDispatcher()) { Thread.sleep(0) }

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

fun getContext(): @BlockingExecutor CoroutineContext = TODO()

fun getNonBlockingContext(): CoroutineContext = TODO()

fun getExplicitlyNonBlockingContext(): @NonBlockingExecutor CoroutineContext = TODO()

@BlockingExecutor
class CustomDispatcher: CoroutineContext {
    override fun <R> fold(initial: R, operation: (R, CoroutineContext.Element) -> R): R = TODO()
    override fun <E : CoroutineContext.Element> get(key: CoroutineContext.Key<E>): E? = TODO()
    override fun minusKey(key: CoroutineContext.Key<*>): CoroutineContext = TODO()
}

suspend fun <T> withContext(
    context: CoroutineContext,
    f: suspend () -> T
) {
    TODO()
}