// "Make block type suspend" "true"
// WITH_STDLIB

import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.createCoroutine

suspend fun <T> suspending(): T {
    val block: () -> T = { null!! }
    return suspendCoroutine { block.<caret>createCoroutine(it) }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddSuspendModifierFix
// IGNORE_K2