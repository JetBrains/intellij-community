// FIR_IDENTICAL
// HIGHLIGHT_WARNINGS
// WITH_COROUTINES
package test

import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job

suspend fun test(job: Job) {
    withContext(EmptyCoroutineContext + NonCancellable + job) {}
}
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines.CoroutineContextWithJobInspection