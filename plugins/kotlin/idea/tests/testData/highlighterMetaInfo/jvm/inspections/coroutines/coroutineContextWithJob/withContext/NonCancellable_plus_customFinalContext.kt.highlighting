// FIR_IDENTICAL
// HIGHLIGHT_WARNINGS
// WITH_COROUTINES
package test

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class MyCustomContextElement : AbstractCoroutineContextElement(key = Companion) {
    companion object : CoroutineContext.Key<MyCustomContextElement>
}

suspend fun test() {
    withContext(NonCancellable + MyCustomContextElement()) {}
}
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines.CoroutineContextWithJobInspection
