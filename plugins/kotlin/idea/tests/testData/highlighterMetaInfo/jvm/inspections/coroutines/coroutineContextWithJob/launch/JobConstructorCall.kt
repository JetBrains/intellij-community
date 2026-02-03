// FIR_IDENTICAL
// HIGHLIGHT_WARNINGS
// WITH_COROUTINES
package test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

fun CoroutineScope.test() {
    launch(Job()) {}
}
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines.CoroutineContextWithJobInspection