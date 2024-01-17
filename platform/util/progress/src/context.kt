// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Experimental

package com.intellij.platform.util.progress

import com.intellij.platform.util.progress.impl.EmptyProgressStep
import com.intellij.platform.util.progress.impl.ProgressStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * ### Fresh step convention
 *
 * Functions assume fresh reporter in the context when called.
 *
 * - Implementation can use [reportSequentialProgress], [reportProgress], [forEachWithProgress],
 * or other functions which rely on [currentProgressStep] without much thinking.
 * - If the context step is not fresh, no reporting from inside the function is visible to the caller,
 * as if there was no progress step in the context at all.
 * - It's up to the caller to provide a fresh step in the context by wrapping the call into [SequentialProgressReporter.sizedStep],
 * [SequentialProgressReporter.indeterminateStep], [ConcurrentProgressReporter.itemStep], etc.
 * - If a function allows to mix reporting done by caller and reporting done by itself,
 * then such function can do this only by declaring a reporter parameter.
 * This is allowed when calling a private function, don't expose concrete reporter in your API.
 *
 * Platform utilities, such as [com.intellij.openapi.progress.coroutineToIndicator], follow this convention.
 *
 * #### Design notes
 *
 * This function is internal to avoid capturing [ProgressStep] instance by clients.
 *
 * @return the current step from the current coroutine context,
 * or `null` if it was not installed into the context
 */
// this function exists here to hold the documentation
internal suspend fun currentProgressStep(): ProgressStep {
  return currentCoroutineContext().currentProgressStep()
}

@PublishedApi
internal suspend fun internalCurrentStepAsSequential(size: Int): SequentialProgressReporterHandle {
  return currentProgressStep().asSequential(size)
         ?: EmptySequentialProgressReporterHandle
}

internal suspend fun internalCurrentStepAsConcurrent(size: Int): ConcurrentProgressReporterHandle {
  return currentProgressStep().asConcurrent(size)
         ?: EmptyConcurrentProgressReporterHandle
}

@PublishedApi
internal suspend fun internalCurrentStepAsRaw(): RawProgressReporterHandle {
  return currentProgressStep().asRaw()
         ?: EmptyRawProgressReporterHandle
}

internal fun ProgressStep.asContextElement(): CoroutineContext.Element {
  return ProgressStepElement(this)
}

private fun CoroutineContext.currentProgressStep(): ProgressStep {
  return this[ProgressStepElement.Key]?.step ?: EmptyProgressStep
}

private class ProgressStepElement(val step: ProgressStep) : AbstractCoroutineContextElement(Key) {
  object Key : CoroutineContext.Key<ProgressStepElement>
}

@Internal // clients are not supposed to put reporter into context
fun ProgressReporter.asContextElement(): CoroutineContext.Element = ProgressReporterElement.Step(this)
val CoroutineContext.progressReporter: ProgressReporter? get() = (this[ProgressReporterElement] as? ProgressReporterElement.Step)?.reporter
val CoroutineScope.progressReporter: ProgressReporter? get() = coroutineContext.progressReporter

@Internal // clients are not supposed to put reporter into context
fun RawProgressReporter.asContextElement(): CoroutineContext.Element = ProgressReporterElement.Raw(this)
val CoroutineContext.rawProgressReporter: RawProgressReporter? get() = (this[ProgressReporterElement] as? ProgressReporterElement.Raw)?.reporter
val CoroutineScope.rawProgressReporter: RawProgressReporter? get() = coroutineContext.rawProgressReporter

private sealed class ProgressReporterElement : AbstractCoroutineContextElement(ProgressReporterElement) {
  companion object : CoroutineContext.Key<ProgressReporterElement>
  class Step(val reporter: ProgressReporter) : ProgressReporterElement()
  class Raw(val reporter: RawProgressReporter) : ProgressReporterElement()
}
