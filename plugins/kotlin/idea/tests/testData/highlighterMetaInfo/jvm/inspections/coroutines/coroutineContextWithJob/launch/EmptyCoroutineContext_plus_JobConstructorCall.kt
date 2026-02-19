// FIR_IDENTICAL
// HIGHLIGHT_WARNINGS
// WITH_COROUTINES
package test

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

fun CoroutineScope.test() {
    launch(EmptyCoroutineContext + Job()) {}

    launch(EmptyCoroutineContext.plus(Job())) {}
}
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines.CoroutineContextWithJobInspection