// "Make block type suspend" "true"
// WITH_STDLIB
// DISABLE-ERRORS

import kotlin.coroutines.experimental.suspendCoroutine

suspend fun <T> suspending(block: () -> T): T = suspendCoroutine { block.<caret>startCoroutine(it) }
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddSuspendModifierFix