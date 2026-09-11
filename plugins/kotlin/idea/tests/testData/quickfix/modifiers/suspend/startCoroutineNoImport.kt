// "Make block type suspend" "true"
// WITH_STDLIB
// DISABLE_ERRORS

import kotlin.coroutines.experimental.suspendCoroutine

suspend fun <T> suspending(block: () -> T): T = suspendCoroutine { block.<caret>startCoroutine(it) }
// IGNORE_K2