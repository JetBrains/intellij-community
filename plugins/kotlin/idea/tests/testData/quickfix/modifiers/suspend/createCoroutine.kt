// "Make block type suspend" "true"
// WITH_STDLIB

import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.createCoroutine

suspend fun <T> suspending(): T {
    val block: () -> T = { null!! }
    return suspendCoroutine { block.<caret>createCoroutine(it) }
}
