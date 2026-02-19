// FIR_IDENTICAL
// HIGHLIGHT_WARNINGS
// WITH_COROUTINES
package test

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun CoroutineScope.test(context: CoroutineContext) {
    launch(context) {}
}
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines.CoroutineContextWithJobInspection