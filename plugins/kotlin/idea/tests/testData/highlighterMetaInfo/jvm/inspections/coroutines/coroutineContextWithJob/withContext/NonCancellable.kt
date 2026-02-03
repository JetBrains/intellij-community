// FIR_IDENTICAL
// HIGHLIGHT_WARNINGS
// WITH_COROUTINES
package test

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

suspend fun test() {
    withContext(NonCancellable) {}
}
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines.CoroutineContextWithJobInspection