// FIR_IDENTICAL
// HIGHLIGHT_WARNINGS
// WITH_COROUTINES
package test

import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

interface NonJob : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<NonJob>
}

fun CoroutineScope.test() {
    launch(coroutineContext.minusKey(NonJob)) {}
}
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines.CoroutineContextWithJobInspection