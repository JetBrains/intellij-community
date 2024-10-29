// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DeprecatedCallableAddReplaceWith")

package com.intellij.platform.util.progress

import com.intellij.concurrency.IntelliJContextElement
import com.intellij.platform.util.progress.impl.EmptyProgressStep
import com.intellij.platform.util.progress.impl.ProgressStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
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
 * [SequentialProgressReporter.indeterminateStep], [ProgressReporter.itemStep], etc.
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

internal suspend fun internalCurrentStepAsConcurrent(size: Int): ProgressReporterHandle {
  return currentProgressStep().asConcurrent(size)
         ?: EmptyProgressReporterHandle
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

private class ProgressStepElement(val step: ProgressStep) : AbstractCoroutineContextElement(Key), IntelliJContextElement {
  override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this

  object Key : CoroutineContext.Key<ProgressStepElement>
}

@Internal
fun CoroutineContext.internalCreateRawHandleFromContextStepIfExistsAndFresh(): RawProgressReporterHandle? {
  return currentProgressStep().asRaw()
}

@Internal // clients are not supposed to put reporter into context
@Deprecated("To report use `reportProgress` or `reportSequentialProgress`. Don't pass as context.")
fun ProgressReporter0.asContextElement(): CoroutineContext.Element = ProgressReporterElement.Step

@get:Internal
@Deprecated("To report use `reportProgress` or `reportSequentialProgress`. Don't pass as context.")
val CoroutineContext.progressReporter: ProgressReporter0? get() = null

@get:Internal
@Deprecated("To report use `reportProgress` or `reportSequentialProgress`. Don't pass as context.")
val CoroutineScope.progressReporter: ProgressReporter0? get() = null

@Internal // clients are not supposed to put reporter into context
@Deprecated(
  "To report use `reportRawProgress`. " +
  "To pass reporter via context implement own context element."
)
fun RawProgressReporter.asContextElement(): CoroutineContext.Element = ProgressReporterElement.Raw(this)

@get:Internal
@Deprecated(
  "To report use `reportRawProgress`. " +
  "To pass reporter via context implement own context element."
)
val CoroutineContext.rawProgressReporter: RawProgressReporter? get() = (this[ProgressReporterElement] as? ProgressReporterElement.Raw)?.reporter

private sealed class ProgressReporterElement : AbstractCoroutineContextElement(ProgressReporterElement), IntelliJContextElement {
  companion object : CoroutineContext.Key<ProgressReporterElement>

  override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this

  data object Step : ProgressReporterElement()
  class Raw(val reporter: RawProgressReporter) : ProgressReporterElement()
}
