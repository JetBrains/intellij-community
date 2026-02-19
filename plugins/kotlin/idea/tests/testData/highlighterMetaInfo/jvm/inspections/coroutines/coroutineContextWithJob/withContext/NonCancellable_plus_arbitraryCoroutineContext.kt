// FIR_IDENTICAL
// HIGHLIGHT_WARNINGS
// WITH_COROUTINES
package test

import kotlin.coroutines.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

suspend fun test(ctx: CoroutineContext) {
    withContext(NonCancellable + ctx) {}
}
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines.CoroutineContextWithJobInspection